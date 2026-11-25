package com.financetracker.category;

import com.financetracker.common.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;



public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    List<Category> findAllByUserIdOrderByTypeAscNameAsc(Long userId);

    List<Category> findAllByUserIdAndTypeOrderByNameAsc(Long userId, TransactionType type);

    boolean existsByUserIdAndTypeAndNameIgnoreCase(Long userId, TransactionType type, String name);

}
