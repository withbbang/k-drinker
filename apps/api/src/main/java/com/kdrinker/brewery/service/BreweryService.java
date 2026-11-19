package com.kdrinker.brewery.service;

import com.kdrinker.brewery.dto.BreweryRequest;
import com.kdrinker.brewery.dto.BreweryResponse;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.event.BreweryUpdatedEvent;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import com.kdrinker.common.InstagramHandles;
import com.kdrinker.common.Names;
import com.kdrinker.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BreweryService {

	private final BreweryRepository breweryRepository;
	private final ApplicationEventPublisher events;

	@Transactional(readOnly = true)
	public PageResponse<BreweryResponse> search(String q, int page, int size) {
		PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id")));
		String normalized = Names.normalize(q);
		var result = normalized.isEmpty() ? breweryRepository.findAll(pageable)
				: breweryRepository.findByNormalizedNameContaining(normalized, pageable);
		return PageResponse.from(result.map(BreweryResponse::from));
	}

	@Transactional(readOnly = true)
	public BreweryResponse get(Long id) {
		return BreweryResponse.from(find(id));
	}

	@Transactional
	public BreweryResponse create(BreweryRequest request) {
		Brewery brewery = Brewery.of(request.name(), Names.normalize(request.name()), BrewerySource.MANUAL);
		applyEdit(brewery, request);
		return BreweryResponse.from(breweryRepository.save(brewery));
	}

	@Transactional
	public BreweryResponse update(Long id, BreweryRequest request) {
		Brewery brewery = find(id);
		applyEdit(brewery, request);
		events.publishEvent(new BreweryUpdatedEvent(id));
		return BreweryResponse.from(brewery);
	}

	/** 다른 서비스의 트랜잭션 안에서도 부를 수 있다. 조회하지 않고 받은 엔티티만 고친다. */
	public void applyEdit(Brewery brewery, BreweryRequest request) {
		String normalizedName = Names.normalize(request.name());
		if (normalizedName.isEmpty()) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		String handle = request.instagramHandle();
		String normalizedHandle = null;
		if (handle != null && !handle.isBlank()) {
			normalizedHandle = InstagramHandles.normalize(handle);
			if (normalizedHandle == null) {
				throw new BusinessException(ErrorCode.BREWERY_INVALID_INSTAGRAM);
			}
		}
		brewery.editManually(request.name().strip(), normalizedName, request.address(), request.phone(),
				request.homepage(), normalizedHandle, request.otherSns());
	}

	private Brewery find(Long id) {
		return breweryRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
	}

}
