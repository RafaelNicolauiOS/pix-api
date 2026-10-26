package com.rafael.pixapi;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// Garante que o schema vem das migrações do Flyway (e não do Hibernate),
// para que H2 (local/testes) e PostgreSQL (produção) tenham exatamente as mesmas tabelas.
@SpringBootTest
class MigracaoBancoTest {

    @Autowired
    private Flyway flyway;

    @Test
    void migracoesDevemEstarAplicadas() {
        MigrationInfo[] aplicadas = flyway.info().applied();

        assertThat(aplicadas).isNotEmpty();
        assertThat(Arrays.stream(aplicadas).map(MigrationInfo::getDescription))
                .contains("criar tabela conta", "criar razao lancamento");
        assertThat(flyway.info().pending()).isEmpty();
    }
}
