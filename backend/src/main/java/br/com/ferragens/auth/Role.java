package br.com.ferragens.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "role")
public class Role {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    protected Role() {
    }

    public String getCode() {
        return code;
    }
}