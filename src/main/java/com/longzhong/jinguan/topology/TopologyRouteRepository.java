package com.longzhong.jinguan.topology;

import org.springframework.data.jpa.repository.JpaRepository;

/** 路由表持久化端口。 */
public interface TopologyRouteRepository extends JpaRepository<TopologyRouteEntity, String> {}
