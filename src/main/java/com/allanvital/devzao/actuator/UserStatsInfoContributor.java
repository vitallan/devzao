package com.allanvital.devzao.actuator;

import jakarta.persistence.EntityManager;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Component
public class UserStatsInfoContributor implements InfoContributor {

    private final EntityManager entityManager;

    public UserStatsInfoContributor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Long total = entityManager.createQuery("SELECT COUNT(g) FROM GitHubUser g", Long.class)
                .getSingleResult();

        List<Object[]> byStatus = entityManager.createQuery(
                        "SELECT g.status, COUNT(g) FROM GitHubUser g GROUP BY g.status",
                        Object[].class)
                .getResultList();

        List<Object[]> byDeepAnalysis = entityManager.createQuery(
                        "SELECT g.deepAnalysisStatus, COUNT(g) FROM GitHubUser g GROUP BY g.deepAnalysisStatus",
                        Object[].class)
                .getResultList();

        builder.withDetail("users", Map.of(
                "total", total,
                "byStatus", toMap(byStatus),
                "byDeepAnalysis", toMap(byDeepAnalysis)
        ));
    }

    private Map<String, Object> toMap(List<Object[]> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                result.put(((Enum<?>) row[0]).name(), row[1]);
            }
        }
        return result;
    }
}
