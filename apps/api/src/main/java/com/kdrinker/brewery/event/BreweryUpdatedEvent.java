package com.kdrinker.brewery.event;

/** 양조장이 수정되어 저장됐을 때 발행된다. */
public record BreweryUpdatedEvent(Long breweryId) {
}
