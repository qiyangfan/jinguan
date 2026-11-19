package com.longzhong.jinguan.topology;

import org.springframework.data.jpa.repository.JpaRepository;

/** 模型能力表持久化端口。 */
public interface TopologyModelRepository
    extends JpaRepository<TopologyModelEntity, TopologyModelEntity.ModelKey> {}
