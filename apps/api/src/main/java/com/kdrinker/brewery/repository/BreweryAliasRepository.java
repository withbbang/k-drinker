package com.kdrinker.brewery.repository;

import com.kdrinker.brewery.entity.BreweryAlias;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BreweryAliasRepository extends JpaRepository<BreweryAlias, Long> {

	@Query("select a from BreweryAlias a join fetch a.brewery where a.normalizedAlias = :normalizedAlias")
	List<BreweryAlias> findByNormalizedAlias(String normalizedAlias);

}
