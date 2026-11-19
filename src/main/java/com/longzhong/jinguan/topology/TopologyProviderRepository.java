package com.longzhong.jinguan.topology;

import org.springframework.data.jpa.repository.JpaRepository;

/** 供应商表持久化端口。 */
public interface TopologyProviderRepository extends JpaRepository<TopologyProviderEntity, String> {}
