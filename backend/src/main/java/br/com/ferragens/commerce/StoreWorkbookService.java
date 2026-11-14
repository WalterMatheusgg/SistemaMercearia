package br.com.ferragens.commerce;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.shared.BusinessRuleException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Relatório de gestão em Excel. Os movimentos são filtrados pela data do fato;
 * posições de estoque e contas em aberto são fotografias do momento do download.
 * Todas as consultas são restritas à loja do usuário autenticado.
 */
@Service
@Transactional(readOnly = true)
public class StoreWorkbookService {
    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int MAX_ROWS = 5000;
    private final Db db;
    private final ReportingService reporting;

    public StoreWorkbookService(Db db, ReportingService reporting) {
        this.db = db;
        this.reporting = reporting;
    }

    private enum Type { TEXT, MONEY, QUANTITY, INTEGER, DATE, DATETIME }
    private record Col(String title, String field, Type type, int width) { }

    private static Col text(String title, String field, int width) {
        return new Col(title, field, Type.TEXT, width);
    }
    private static Col money(String title, String field) {
        return new Col(title, field, Type.MONEY, 18);
    }
    private static Col quantity(String title, String field) {
        return new Col(title, field, Type.QUANTITY, 16);
    }
    private static Col date(String title, String field) {
        return new Col(title, field, Type.DATE, 16);
    }
    private static Col time(String title, String field) {
        return new Col(title, field, Type.DATETIME, 23);
    }

    public byte[] export(Actor actor, LocalDate start, LocalDate end) {
        // Reaproveita validação e definições contábeis existentes na aba Relatórios.
        Map<String, Object> report = reporting.report(actor, start, end);
        String period = "Período: " + start + " a " + end + " • Brasília (America/Sao_Paulo)";
        OffsetDateTime from = start.atStartOfDay(Db.ZONE).toOffsetDateTime();
        OffsetDateTime to = end.plusDays(1).atStartOfDay(Db.ZONE).toOffsetDateTime();
        UUID storeId = actor.storeId();

        List<Map<String, Object>> sales = rows("""
                SELECT id,confirmed_at,customer_name,channel,status,subtotal,discount,
                       delivery_fee,total
                FROM sale WHERE store_id=? AND confirmed_at>=? AND confirmed_at<?
                  AND status NOT IN ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT')
                ORDER BY confirmed_at,id
                """, storeId, from, to);
        List<Map<String, Object>> saleItems = rows("""
                SELECT s.confirmed_at,s.id AS sale_id,i.code,i.name AS product,
                       i.unit_code AS unit,i.quantity,i.unit_price,i.unit_cost,
                       i.line_total,(i.line_total-i.quantity*i.unit_cost) AS estimated_gross
                FROM sale_item i JOIN sale s ON s.id=i.sale_id
                WHERE s.store_id=? AND s.confirmed_at>=? AND s.confirmed_at<?
                  AND s.status NOT IN ('CANCELLED','EXPIRED','QUOTE','PENDING_PAYMENT')
                ORDER BY s.confirmed_at,s.id,i.name
                """, storeId, from, to);
        List<Map<String, Object>> movements = rows("""
                SELECT m.occurred_at,l.name AS location,m.kind,
                       m.product_code AS code,m.product_name AS product,m.unit_code AS unit,
                       m.quantity,m.delta,m.balance_before,m.balance_after,m.actor_name AS actor,
                       m.reason,CASE WHEN EXISTS (
                           SELECT 1 FROM inventory_movement r WHERE r.reverses_movement_id=m.id
                       ) THEN 'Sim' ELSE 'Não' END AS reversed
                FROM inventory_movement m
                JOIN inventory_location l ON l.id=m.location_id AND l.store_id=m.store_id
                WHERE m.store_id=? AND m.occurred_at>=? AND m.occurred_at<?
                ORDER BY m.occurred_at,m.id
                """, storeId, from, to);
        List<Map<String, Object>> stock = rows("""
                SELECT l.name AS location,p.code,p.name AS product,p.brand,
                       c.name AS category,p.base_unit_code AS unit,b.physical,b.reserved,
                       b.unavailable,(b.physical-b.reserved-b.unavailable) AS available,
                       b.minimum_quantity,p.cost,p.price,(b.physical*p.cost) AS stock_cost,
                       b.updated_at
                FROM inventory_balance b JOIN inventory_location l ON l.id=b.location_id
                JOIN product p ON p.id=b.product_id JOIN category c ON c.id=p.category_id
                WHERE l.store_id=? AND p.active
                ORDER BY l.name,p.name,p.code
                """, storeId);
        List<Map<String, Object>> payments = rows("""
                SELECT p.occurred_at,a.description,p.direction,p.method,p.amount,
                       CASE WHEN p.direction='IN' THEN p.amount ELSE -p.amount END AS net,
                       p.note,p.reversed,p.reversal_of
                FROM financial_payment p JOIN financial_account a ON a.id=p.account_id
                WHERE a.store_id=? AND p.occurred_at>=? AND p.occurred_at<?
                ORDER BY p.occurred_at,p.id
                """, storeId, from, to);
        List<Map<String, Object>> accounts = rows("""
                SELECT a.created_at,a.direction,a.description,
                       coalesce(p.name,'') AS party,a.amount,a.paid,
                       (a.amount-a.paid) AS outstanding,a.status,a.due_date
                FROM financial_account a LEFT JOIN party p ON p.id=a.party_id
                WHERE a.store_id=? AND a.created_at>=? AND a.created_at<?
                ORDER BY a.created_at,a.id
                """, storeId, from, to);
        List<Map<String, Object>> cash = rows("""
                SELECT e.occurred_at,e.kind,e.amount,e.reason,u.username AS actor,
                       c.opened_at AS session_opened_at
                FROM cash_entry e JOIN cash_session c ON c.id=e.session_id
                JOIN app_user u ON u.id=e.actor_id
                WHERE c.store_id=? AND e.occurred_at>=? AND e.occurred_at<?
                ORDER BY e.occurred_at,e.id
                """, storeId, from, to);
        List<Map<String, Object>> purchases = rows("""
                SELECT o.created_at,o.supplier_name,o.status,o.total,o.due_date,
                       o.note,o.id
                FROM purchase_order o WHERE o.store_id=?
                  AND o.created_at>=? AND o.created_at<?
                ORDER BY o.created_at,o.id
                """, storeId, from, to);
        List<Map<String, Object>> receipts = rows("""
                SELECT r.received_at,o.supplier_name,o.id AS purchase_id,
                       r.total,r.cancelled_at,r.note
                FROM purchase_receipt r JOIN purchase_order o ON o.id=r.purchase_id
                WHERE o.store_id=? AND r.received_at>=? AND r.received_at<?
                ORDER BY r.received_at,r.id
                """, storeId, from, to);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            writeSummary(workbook, styles, report, start, end, actor.username(),
                    sales.size(), movements, payments, receipts.size());
            section(workbook, styles, "Vendas", period, sales,
                    time("Confirmada em","confirmedAt"),
                    text("Cliente","customerName",30),text("Canal","channel",16),
                    text("Status","status",19),money("Subtotal","subtotal"),
                    money("Desconto","discount"),money("Frete","deliveryFee"),
                    money("Total","total"),text("ID da venda","id",38));
            section(workbook, styles, "Itens vendidos", period, saleItems,
                    time("Confirmada em","confirmedAt"),text("Código","code",19),
                    text("Produto","product",38),text("Unidade","unit",11),
                    quantity("Quantidade","quantity"),money("Preço unitário","unitPrice"),
                    money("Custo histórico un.","unitCost"),money("Total vendido","lineTotal"),
                    money("Margem bruta estimada","estimatedGross"),
                    text("ID da venda","saleId",38));
            section(workbook, styles, "Entradas e saídas", period, movements,
                    time("Data/hora","occurredAt"),text("Local","location",24),
                    text("Operação","kind",17),text("Código","code",19),
                    text("Material","product",38),text("Unidade","unit",11),
                    quantity("Quantidade informada","quantity"),
                    quantity("Variação (+/-)","delta"),
                    quantity("Saldo anterior","balanceBefore"),
                    quantity("Saldo após","balanceAfter"),
                    text("Operador","actor",19),text("Motivo","reason",50),
                    text("Revertido?","reversed",14));
            section(workbook, styles, "Estoque atual",
                    "POSIÇÃO ATUAL: " + ZonedDateTime.now(Db.ZONE).format(
                            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                            + " • não representa saldo retroativo de " + period,
                    stock,text("Local","location",24),text("Código","code",19),
                    text("Produto","product",38),text("Marca","brand",22),
                    text("Categoria","category",20),text("Unidade","unit",11),
                    quantity("Saldo físico","physical"),quantity("Reservado","reserved"),
                    quantity("Indisponível","unavailable"),
                    quantity("Disponível","available"),quantity("Estoque mínimo","minimumQuantity"),
                    money("Custo unit.","cost"),money("Preço unit.","price"),
                    money("Estoque a custo","stockCost"),time("Atualizado em","updatedAt"));
            section(workbook, styles, "Pagamentos", period, payments,
                    time("Data/hora","occurredAt"),text("Conta","description",45),
                    text("Direção","direction",13),text("Meio","method",18),
                    money("Valor","amount"),money("Fluxo líquido (+/-)","net"),
                    text("Observação","note",38),text("Estornado?","reversed",15),
                    text("Estorno de (ID)","reversalOf",38));
            section(workbook, styles, "Contas", "CONTAS CRIADAS NO PERÍODO • pago e saldo são posições atuais",
                    accounts,time("Criada em","createdAt"),
                    text("Tipo","direction",18),text("Descrição","description",44),
                    text("Cliente / fornecedor","party",30),money("Valor original","amount"),
                    money("Pago até agora","paid"),money("Saldo atual","outstanding"),
                    text("Status atual","status",17),date("Vencimento","dueDate"));
            section(workbook, styles, "Movimentos de caixa", period, cash,
                    time("Data/hora","occurredAt"),text("Tipo","kind",18),
                    money("Entrada / saída (+/-)","amount"),text("Motivo","reason",42),
                    text("Operador","actor",20),time("Abertura do caixa","sessionOpenedAt"));
            section(workbook, styles, "Compras", "COMPRAS CRIADAS NO PERÍODO",
                    purchases,time("Criada em","createdAt"),
                    text("Fornecedor","supplierName",32),text("Status atual","status",18),
                    money("Valor previsto","total"),date("Vencimento","dueDate"),
                    text("Observação","note",45),text("ID do pedido","id",38));
            section(workbook, styles, "Receb. compras", "RECEBIMENTOS DE COMPRAS NO PERÍODO",
                    receipts,time("Recebido em","receivedAt"),
                    text("Fornecedor","supplierName",32),money("Valor recebido","total"),
                    time("Cancelado em","cancelledAt"),text("Observação","note",44),
                    text("ID da compra","purchaseId",38));

            workbook.setActiveSheet(0);
            workbook.write(bytes);
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gerar a planilha da loja.", ex);
        }
    }

    private List<Map<String, Object>> rows(String sql, Object... params) {
        List<Map<String, Object>> result = db.rows(sql.stripTrailing() + " LIMIT " + (MAX_ROWS + 1), params);
        if (result.size() > MAX_ROWS) {
            throw new BusinessRuleException("O período possui mais de " + MAX_ROWS
                    + " registros em uma seção da planilha. Escolha um intervalo menor e exporte novamente.");
        }
        return result;
    }

    private void writeSummary(XSSFWorkbook book, Styles styles, Map<String, Object> report,
            LocalDate from, LocalDate to, String actor, int sales,
            List<Map<String, Object>> movements, List<Map<String, Object>> payments, int receipts) {
        Sheet sheet = book.createSheet("Resumo");
        sheet.setColumnWidth(0, 46 * 256);
        sheet.setColumnWidth(1, 31 * 256);
        sheet.setColumnWidth(2, 28 * 256);
        int r = 0;
        Row heading = sheet.createRow(r++);
        Cell title = heading.createCell(0);
        title.setCellValue("RELATÓRIO GERENCIAL • LOJA FERRAGENS");
        title.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0,0,0,2));
        r = summary(sheet,styles,r,"Início do período",from.toString(),Type.TEXT);
        r = summary(sheet,styles,r,"Fim do período",to.toString(),Type.TEXT);
        r = summary(sheet,styles,r,"Gerado em",
                ZonedDateTime.now(Db.ZONE).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),Type.TEXT);
        r = summary(sheet,styles,r,"Exportado por",actor,Type.TEXT);
        r++;
        r = summary(sheet,styles,r,"Vendas confirmadas",report.get("sales"),Type.INTEGER);
        r = summary(sheet,styles,r,"Faturamento em vendas",report.get("revenue"),Type.MONEY);
        r = summary(sheet,styles,r,"Ticket médio",report.get("averageTicket"),Type.MONEY);
        r = summary(sheet,styles,r,"Descontos concedidos",report.get("discounts"),Type.MONEY);
        r = summary(sheet,styles,r,"Receita mercadorias após desconto",report.get("merchandiseRevenue"),Type.MONEY);
        r = summary(sheet,styles,r,"Custo histórico das mercadorias",report.get("goodsCost"),Type.MONEY);
        r = summary(sheet,styles,r,"Lucro bruto estimado",report.get("grossProfit"),Type.MONEY);
        r = summary(sheet,styles,r,"Faturamento período anterior",report.get("previousRevenue"),Type.MONEY);
        r++;
        r = summary(sheet,styles,r,"Registros de vendas exportados",sales,Type.INTEGER);
        r = summary(sheet,styles,r,"Movimentações no estoque",movements.size(),Type.INTEGER);
        r = summary(sheet,styles,r,"Ocorrências de entrada (+)",
                movements.stream().filter(m -> numeric(m.get("delta")).signum()>0).count(),Type.INTEGER);
        r = summary(sheet,styles,r,"Ocorrências de saída (-)",
                movements.stream().filter(m -> numeric(m.get("delta")).signum()<0).count(),Type.INTEGER);
        r = summary(sheet,styles,r,"Pagamentos registrados",payments.size(),Type.INTEGER);
        r = summary(sheet,styles,r,"Recebimentos de compras",receipts,Type.INTEGER);
        r++;
        r = summary(sheet,styles,r,"A receber • posição atual",report.get("receivables"),Type.MONEY);
        r = summary(sheet,styles,r,"A pagar • posição atual",report.get("payables"),Type.MONEY);
        r = summary(sheet,styles,r,"Estoque físico a custo • posição atual",
                report.get("stockCostValue"),Type.MONEY);
        r = summary(sheet,styles,r,"Materiais com saldo positivo • agora",
                report.get("stockedProducts"),Type.INTEGER);
        r += 2;
        for (String note : List.of(
                "Vendas: pela confirmação; excluem canceladas, cotações e reservas pendentes.",
                "Estoque: entradas e saídas pela variação real de quantidade, inclusive reversões.",
                "Contas: criadas no intervalo; saldos, estoque e status refletem o momento do download.",
                "Pagamentos e movimentos físicos de caixa podem representar o mesmo dinheiro: NÃO somar as abas.",
                "Lucro bruto não é lucro líquido; não considera impostos, salários ou todas as despesas.",
                "Todos os horários das operações são apresentados no fuso America/Sao_Paulo."
        )) {
            Row row=sheet.createRow(r++);
            Cell cell=row.createCell(0);
            cell.setCellValue(note);
            cell.setCellStyle(styles.note);
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(),row.getRowNum(),0,2));
            row.setHeightInPoints(25);
        }
        sheet.createFreezePane(0,1);
    }

    private int summary(Sheet sheet, Styles styles, int r, String name, Object value, Type type) {
        Row row=sheet.createRow(r++);
        Cell label=row.createCell(0);
        label.setCellValue(name);
        label.setCellStyle(styles.label);
        Cell data=row.createCell(1);
        put(data,value,type,styles);
        return r;
    }

    private void section(XSSFWorkbook book, Styles styles, String name, String subtitle,
            List<Map<String, Object>> values, Col... columns) {
        Sheet sheet=book.createSheet(name);
        for(int c=0;c<columns.length;c++) sheet.setColumnWidth(c,columns[c].width()*256);
        Row title=sheet.createRow(0);
        title.setHeightInPoints(32);
        Cell titleCell=title.createCell(0);
        titleCell.setCellValue(name.toUpperCase(Locale.forLanguageTag("pt-BR")));
        titleCell.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0,0,0,columns.length-1));
        Row subtitleRow=sheet.createRow(1);
        Cell subtitleCell=subtitleRow.createCell(0);
        subtitleCell.setCellValue(subtitle);
        subtitleCell.setCellStyle(styles.subtitle);
        sheet.addMergedRegion(new CellRangeAddress(1,1,0,columns.length-1));
        Row header=sheet.createRow(3);
        header.setHeightInPoints(25);
        for(int c=0;c<columns.length;c++){
            Cell cell=header.createCell(c);
            cell.setCellValue(columns[c].title());
            cell.setCellStyle(styles.header);
        }
        int index=4;
        for(Map<String,Object> data:values){
            Row row=sheet.createRow(index++);
            for(int c=0;c<columns.length;c++) {
                Cell cell=row.createCell(c);
                put(cell,data.get(columns[c].field()),columns[c].type(),styles);
                if(columns[c].type()==Type.TEXT && index%2==0)
                    cell.setCellStyle(styles.textAlternate);
            }
        }
        sheet.createFreezePane(0,4);
        sheet.setAutoFilter(new CellRangeAddress(3,Math.max(3,index-1),0,columns.length-1));
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(true);
        sheet.setRepeatingRows(CellRangeAddress.valueOf("4:4"));
    }

    private static BigDecimal numeric(Object value) {
        if(value==null||value.toString().isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.toString());
    }

    private static void put(Cell cell, Object raw, Type type, Styles s) {
        if(raw==null){ cell.setBlank();cell.setCellStyle(s.body);return; }
        String text=raw.toString();
        switch(type) {
            case MONEY,QUANTITY,INTEGER -> {
                cell.setCellValue(numeric(raw).doubleValue());
                cell.setCellStyle(type==Type.MONEY?s.money:
                                  type==Type.QUANTITY?s.quantity:s.integer);
            }
            case DATE -> {
                cell.setCellValue(LocalDate.parse(text));
                cell.setCellStyle(s.date);
            }
            case DATETIME -> {
                // Fuso de Brasília para exibir a hora correta no Excel,
                // independentemente do fuso da JVM/contêiner.
                cell.setCellValue(Instant.parse(text).atZone(Db.ZONE).toLocalDateTime());
                cell.setCellStyle(s.datetime);
            }
            case TEXT -> {
                if(raw instanceof Boolean flag) text=flag ? "Sim" : "Não";
                // setCellValue escreve string explícita, nunca fórmula executável.
                cell.setCellValue(text.length()>32767?text.substring(0,32767):text);
                cell.setCellStyle(s.body);
            }
        }
    }

    private static final class Styles {
        final CellStyle title,subtitle,header,body,textAlternate,label,note;
        final CellStyle money,quantity,integer,date,datetime;
        Styles(XSSFWorkbook book){
            DataFormat format=book.createDataFormat();
            Font big=book.createFont();
            big.setBold(true);big.setFontHeightInPoints((short)16);
            big.setColor(IndexedColors.WHITE.getIndex());
            title=book.createCellStyle();title.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);title.setFont(big);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            Font small=book.createFont();small.setItalic(true);small.setFontHeightInPoints((short)10);
            small.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subtitle=book.createCellStyle();subtitle.setFont(small);
            Font head=book.createFont();head.setBold(true);
            head.setColor(IndexedColors.WHITE.getIndex());
            header=book.createCellStyle();header.setFont(head);
            header.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setWrapText(true);header.setVerticalAlignment(VerticalAlignment.CENTER);
            body=book.createCellStyle();body.setVerticalAlignment(VerticalAlignment.CENTER);
            textAlternate=book.createCellStyle();textAlternate.cloneStyleFrom(body);
            textAlternate.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            textAlternate.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font labelFont=book.createFont();labelFont.setBold(true);
            label=book.createCellStyle();label.setFont(labelFont);
            note=book.createCellStyle();note.cloneStyleFrom(subtitle);note.setWrapText(true);
            money=book.createCellStyle();money.setDataFormat(format.getFormat("\"R$\" #,##0.00;[Red](\"R$\" #,##0.00)"));
            quantity=book.createCellStyle();quantity.setDataFormat(format.getFormat("#,##0.####"));
            integer=book.createCellStyle();integer.setDataFormat(format.getFormat("#,##0"));
            date=book.createCellStyle();date.setDataFormat(format.getFormat("dd/mm/yyyy"));
            datetime=book.createCellStyle();datetime.setDataFormat(format.getFormat("dd/mm/yyyy hh:mm"));
        }
    }
}
