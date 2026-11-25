package com.financetracker.category;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.financetracker.common.domain.TransactionType;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.ResourceNotFoundException;
import com.financetracker.common.security.CurrentUserProvider;


@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final CurrentUserProvider currentUserProvider;

    // constructor injection, with no @Autowired needed for a single constructor
    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper, CurrentUserProvider currentUserProvider) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
        this.currentUserProvider = currentUserProvider;
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
}
