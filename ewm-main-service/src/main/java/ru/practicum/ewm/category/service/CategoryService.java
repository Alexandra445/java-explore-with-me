package ru.practicum.ewm.category.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.dto.NewCategoryDto;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.common.OffsetPageRequest;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    @Transactional
    public CategoryDto createCategory(NewCategoryDto request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new ConflictException("Category with name=" + request.getName() + " already exists");
        }

        Category category = new Category();
        category.setName(request.getName());

        return toDto(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto updateCategory(Long categoryId, NewCategoryDto request) {
        Category category = getCategoryOrThrow(categoryId);

        if (!category.getName().equals(request.getName())
                && categoryRepository.existsByName(request.getName())) {
            throw new ConflictException("Category with name=" + request.getName() + " already exists");
        }

        category.setName(request.getName());

        return toDto(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = getCategoryOrThrow(categoryId);

        if (eventRepository.existsByCategoryId(categoryId)) {
            throw new ConflictException("The category is not empty");
        }

        categoryRepository.delete(category);
    }

    public List<CategoryDto> getCategories(int from, int size) {
        return categoryRepository.findAllBy(OffsetPageRequest.of(from, size, Sort.by("id")))
                .stream()
                .map(this::toDto)
                .toList();
    }

    public CategoryDto getCategory(Long categoryId) {
        return toDto(getCategoryOrThrow(categoryId));
    }

    private Category getCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException(
                        "Category with id=" + categoryId + " was not found"));
    }

    private CategoryDto toDto(Category category) {
        return new CategoryDto(category.getId(), category.getName());
    }
}