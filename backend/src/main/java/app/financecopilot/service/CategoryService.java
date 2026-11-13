package app.financecopilot.service;

import app.financecopilot.domain.Category;
import app.financecopilot.domain.CategoryKind;
import app.financecopilot.dto.TransactionDtos.CategoryResponse;
import app.financecopilot.dto.TransactionDtos.CreateCategoryRequest;
import app.financecopilot.exception.ApiException;
import app.financecopilot.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId) {
        return categories.findVisibleTo(userId).stream().map(CategoryService::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CreateCategoryRequest request) {
        String name = request.name().strip();
        boolean clash = categories.findVisibleTo(userId).stream().anyMatch(c -> c.getName().equalsIgnoreCase(name));
        if (clash) {
            throw ApiException.conflict("A category named '" + name + "' already exists");
        }
        CategoryKind kind = request.kind() == null ? CategoryKind.EXPENSE : request.kind();
        String color = request.color() == null ? "#64748b" : request.color();
        return toResponse(categories.save(new Category(userId, name, kind, color)));
    }

    /** Resolves a category id the user may use: a system category or one of their own. */
    @Transactional(readOnly = true)
    public Category requireVisible(Long userId, Long categoryId) {
        return categories.findVisibleById(categoryId, userId).orElseThrow(() -> ApiException.notFound("Category"));
    }

    public static CategoryResponse toResponse(Category c) {
        return c == null ? null : new CategoryResponse(c.getId(), c.getName(), c.getKind(), c.getColor(), c.isSystem());
    }
}
