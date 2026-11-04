package br.com.ferragens.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "unit")
public class Unit {

    @Id
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected Unit() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}