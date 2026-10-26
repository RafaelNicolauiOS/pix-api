package com.rafael.pixapi.repository;

import com.rafael.pixapi.domain.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    // Razão completo até o instante de corte, em ordem cronológica (usado na exportação noturna).
    List<Lancamento> findByRealizadoEmLessThanEqualOrderByRealizadoEmAscIdAsc(Instant corte);
}
