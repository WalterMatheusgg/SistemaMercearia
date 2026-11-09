import { AfterViewInit, Component, ElementRef, EventEmitter, OnDestroy, Output, ViewChild, signal } from '@angular/core';

/**
 * Câmera somente no navegador, com processamento local das imagens via ZXing.
 * Não envia vídeo ao servidor e nunca movimenta o estoque por conta própria.
 */
@Component({
  selector: 'app-barcode-camera',
  standalone: true,
  template: `
    <section class="camera-reader" role="region" aria-label="Leitor de etiquetas pela câmera">
      <div class="camera-heading">
        <strong>Escanear etiqueta com a câmera</strong>
        <button type="button" (click)="close()">Fechar câmera</button>
      </div>
      <div class="camera-frame">
        <video #preview autoplay muted playsinline aria-label="Imagem ao vivo da câmera"></video>
        <div class="aim" aria-hidden="true"><span></span></div>
      </div>
      @if (error()) {
        <p class="camera-error" role="alert">{{ error() }}</p>
      } @else {
        <p class="camera-hint" role="status">{{ status() }}</p>
      }
      <p class="camera-help">Aponte para as barras da etiqueta LF. Mantenha o código inteiro visível e bem iluminado.</p>
    </section>
  `,
  styles: [`
    :host{display:block;min-width:0}
    .camera-reader{display:grid;gap:10px;padding:12px;border:1px solid #bad7c3;border-radius:13px;background:#eef8f1}
    .camera-heading{display:flex;gap:12px;align-items:center;justify-content:space-between;flex-wrap:wrap}
    .camera-heading strong{font-size:13px;color:#214c3a}
    .camera-heading button{min-height:44px;padding:8px 13px;border:1px solid #9bc4ad;border-radius:9px;background:#fff;color:#15573e;font-weight:750;cursor:pointer}
    .camera-frame{position:relative;overflow:hidden;border-radius:12px;background:#111;aspect-ratio:4/3;max-height:340px}
    video{display:block;width:100%;height:100%;object-fit:cover}
    .aim{position:absolute;inset:20% 12%;border:2px solid #a3efb7;border-radius:9px;box-shadow:0 0 0 150vmax #0004;pointer-events:none}
    .aim span{position:absolute;left:7%;right:7%;top:50%;height:2px;background:#a3efb7;box-shadow:0 0 9px #b0fbc5}
    .camera-hint,.camera-help,.camera-error{margin:0;font-size:12px;line-height:1.5}
    .camera-hint{color:#205d42;font-weight:700}
    .camera-help{color:#526d60}
    .camera-error{padding:10px;border-radius:9px;background:#fff1ef;color:#a33c33}
    @media(max-width:560px){.camera-reader{padding:10px}.camera-frame{max-height:290px}}
  `],
})
export class BarcodeCameraComponent implements AfterViewInit, OnDestroy {
  @ViewChild('preview', { static: true }) private preview!: ElementRef<HTMLVideoElement>;
  @Output() readonly scanned = new EventEmitter<string>();
  @Output() readonly closed = new EventEmitter<void>();

  readonly status = signal('Solicitando permissão para usar a câmera...');
  readonly error = signal('');
  private controls?: { stop(): void };
  private disposed = false;
  private detected = false;
  private readonly onVisibility = () => {
    if (document.hidden && !this.disposed) this.close();
  };

  ngAfterViewInit(): void {
    document.addEventListener('visibilitychange', this.onVisibility);
    void this.start();
  }

  private async start(): Promise<void> {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      this.error.set('A câmera exige HTTPS e um navegador com acesso à câmera. Use o leitor USB/Bluetooth ou digite o código.');
      return;
    }
    try {
      // Carregamento sob demanda: só baixa a biblioteca quando a câmera é aberta.
      const { BrowserMultiFormatOneDReader } = await import('@zxing/browser');
      if (this.disposed) return;
      const reader = new BrowserMultiFormatOneDReader();
      const controls = await reader.decodeFromConstraints(
        { audio: false, video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } } },
        this.preview.nativeElement,
        (result, _error, scanControls) => {
          if (this.disposed || this.detected || !result) return;
          const code = result.getText().trim().toUpperCase();
          if (!/^LF\d{10}$/.test(code)) {
            this.status.set('Código diferente do padrão LF. Aponte para uma etiqueta da Loja.');
            return;
          }
          this.detected = true;
          scanControls.stop();
          this.scanned.emit(code);
        },
      );
      if (this.disposed || this.detected) controls.stop();
      else {
        this.controls = controls;
        this.status.set('Câmera ativa. Centralize o código de barras no quadro.');
      }
    } catch (e) {
      if (this.disposed) return;
      const name = e instanceof Error ? e.name : '';
      this.error.set(
        name === 'NotAllowedError' || name === 'PermissionDeniedError'
          ? 'Permissão da câmera negada. Autorize nas configurações do navegador ou use a digitação.'
          : name === 'NotFoundError' || name === 'DevicesNotFoundError'
            ? 'Nenhuma câmera disponível. Conecte uma câmera ou digite o código.'
            : 'Não foi possível iniciar a câmera. Tente outro navegador ou use o leitor e a digitação.',
      );
      this.stopVideo();
    }
  }

  close(): void {
    this.closed.emit();
  }

  private stopVideo(): void {
    this.controls?.stop();
    this.controls = undefined;
    const video = this.preview?.nativeElement;
    const stream = video?.srcObject as MediaStream | null | undefined;
    stream?.getTracks?.().forEach(track => track.stop());
    if (video) video.srcObject = null;
  }

  ngOnDestroy(): void {
    this.disposed = true;
    document.removeEventListener('visibilitychange', this.onVisibility);
    this.stopVideo();
  }
}
