package com.longzhong.jinguan.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

/** 价目持久化端口。 */
public interface PricingEntryRepository extends JpaRepository<PricingEntryEntity, String> {}
