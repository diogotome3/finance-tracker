package com.financetracker.category;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.financetracker.common.domain.TransactionType;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.ResourceNotFoundException;
import com.financetracker.common.security.CurrentUserProvider;
import com.financetracker.transaction.TransactionRepository;


@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionRepository transactionRepository;

    // constructor injection, with no @Autowired needed for a single constructor
    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper, CurrentUserProvider currentUserProvider, TransactionRepository transactionRepository) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
        this.currentUserProvider = currentUserProvider;
        this.transactionRepository = transactionRepository;
    }

    public List<CategoryResponse> list(TransactionType type) {
        long userId = currentUserProvider.currentUserId();
        if (type == null) {
            return categoryRepository.findAllByUserIdOrderByTypeAscNameAsc(userId).stream()
                    .map(categoryMapper::toResponse)
                    .toList();
        } else {
            return categoryRepository.findAllByUserIdAndTypeOrderByNameAsc(userId, type).stream()
                    .map(categoryMapper::toResponse)
                    .toList();
        }
    }

    public CategoryResponse get(long id) {
        long userId = currentUserProvider.currentUserId();
        return categoryRepository.findByIdAndUserId(id, userId)
                .map(categoryMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        long userId = currentUserProvider.currentUserId();
        if (categoryRepository.existsByUserIdAndTypeAndNameIgnoreCase(userId, request.type(), request.name())) {
            // "An" fits both types: expense, income.
            throw new ConflictException("An " + request.type().name().toLowerCase() + " category named '"
                    + request.name() + "' already exists");
        }
        Category category = categoryMapper.toEntity(request, userId);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(long id, CategoryRequest request) {
        long userId = currentUserProvider.currentUserId();
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
        if (request.type() != category.getType()
                && transactionRepository.existsByCategoryId(id)) {
            throw new ConflictException("Cannot change the type of a category that has transactions");
        }
        if (categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(userId, request.type(), request.name(), id)) {
            throw new ConflictException("An " + request.type().name().toLowerCase() + " category named '"
                    + request.name() + "' already exists");
        }
        categoryMapper.updateEntity(category, request);
        // Flush now, not at commit: auditing sets updatedAt during the flush, and the response is built from it.
        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public void delete(long id) {
        long userId = currentUserProvider.currentUserId();
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
        long count = transactionRepository.countByCategoryId(id);
        if (count > 0) {
            throw new ConflictException("Category is used by " + count + " transaction" + (count > 1 ? "s" : "") + ".");
        }
        categoryRepository.delete(category);
    }
}
