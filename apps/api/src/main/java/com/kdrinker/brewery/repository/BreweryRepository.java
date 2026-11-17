package com.kdrinker.brewery.repository;

import com.kdrinker.brewery.entity.Brewery;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreweryRepository extends JpaRepository<Brewery, Long> {

	List<Brewery> findByNormalizedName(String normalizedName);

	Page<Brewery> findByNormalizedNameContaining(String normalizedName, Pageable pageable);

}
