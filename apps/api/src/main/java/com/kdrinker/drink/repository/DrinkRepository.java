package com.kdrinker.drink.repository;

import com.kdrinker.drink.entity.Drink;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrinkRepository extends JpaRepository<Drink, Long> {

	List<Drink> findByNormalizedName(String normalizedName);

	List<Drink> findByAliasesIsNotNull();

	@EntityGraph(attributePaths = "brewery")
	List<Drink> findTop20ByNormalizedNameContainingOrderByNameAsc(String normalizedName);

}
