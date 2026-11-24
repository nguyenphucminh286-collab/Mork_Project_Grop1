package vn.edu.fpt.savora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.savora.entity.Dish;
import vn.edu.fpt.savora.enums.DishStatus;

import java.util.List;

@Repository
public interface DishRepository extends JpaRepository<Dish, Integer> {

    List<Dish> findByStatusOrderByIsSignatureDescDishIdAsc(DishStatus status);
}
