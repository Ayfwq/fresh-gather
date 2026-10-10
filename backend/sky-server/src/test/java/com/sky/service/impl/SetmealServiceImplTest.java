package com.sky.service.impl;

import com.sky.dto.SetmealDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.BusinessException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetmealServiceImplTest {

    @Mock private SetmealMapper setmealMapper;
    @Mock private SetmealDishMapper setmealDishMapper;
    @Mock private DishMapper dishMapper;
    @InjectMocks private SetmealServiceImpl setmealService;

    @Test
    void saveUsesCurrentIngredientPricesAndAppliesFivePercentDiscount() {
        when(dishMapper.getById(11L)).thenReturn(dish(11L, "牛腩", "12.50"));
        when(dishMapper.getById(12L)).thenReturn(dish(12L, "土豆", "7.30"));
        SetmealDish beef = ingredient(11L, 1);
        SetmealDish potato = ingredient(12L, 2);
        SetmealDTO request = new SetmealDTO();
        request.setName("土豆炖牛腩食材套餐");
        request.setPrice(new BigDecimal("999.99")); // Submitted prices are never trusted.
        request.setSetmealDishes(List.of(beef, potato));

        setmealService.saveWithDish(request);

        ArgumentCaptor<Setmeal> saved = ArgumentCaptor.forClass(Setmeal.class);
        verify(setmealMapper).insert(saved.capture());
        assertEquals(new BigDecimal("25.75"), saved.getValue().getPrice());
        assertEquals("牛腩", beef.getName());
        assertEquals(new BigDecimal("12.50"), beef.getPrice());
        assertEquals("土豆", potato.getName());
        assertEquals(new BigDecimal("7.30"), potato.getPrice());
        verify(setmealDishMapper).insertBatch(any());
    }

    @Test
    void saveRejectsAnEmptyIngredientBundle() {
        SetmealDTO request = new SetmealDTO();
        request.setSetmealDishes(List.of());

        assertThrows(BusinessException.class, () -> setmealService.saveWithDish(request));
    }

    private Dish dish(Long id, String name, String price) {
        return Dish.builder().id(id).name(name).price(new BigDecimal(price)).status(1).build();
    }

    private SetmealDish ingredient(Long dishId, Integer copies) {
        SetmealDish ingredient = new SetmealDish();
        ingredient.setDishId(dishId);
        ingredient.setCopies(copies);
        return ingredient;
    }
}
