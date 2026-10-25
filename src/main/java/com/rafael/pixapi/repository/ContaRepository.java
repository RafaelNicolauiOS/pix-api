package com.rafael.pixapi.repository;

import com.rafael.pixapi.domain.Conta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

// Só a interface: o Spring Data gera a implementação em tempo de execução.
// JpaRepository já traz save, findById, findAll, deleteById, existsById...
public interface ContaRepository extends JpaRepository<Conta, Long> {

    // Query derivada do nome do método: SELECT COUNT(*) > 0 FROM conta WHERE chave_pix = ?
    boolean existsByChavePix(String chavePix);

    // SELECT ... FOR UPDATE: trava a linha até o fim da transação, para que duas
    // transferências simultâneas não leiam o mesmo saldo e gastem o dinheiro duas vezes.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Conta> findComLockByChavePix(String chavePix);
}
