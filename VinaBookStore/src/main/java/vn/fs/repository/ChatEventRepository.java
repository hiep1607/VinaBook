package vn.fs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.fs.entity.ChatEvent;

@Repository
public interface ChatEventRepository extends JpaRepository<ChatEvent, String> {
    interface Summary { Long getTotal();Long getFallback();Long getPositive();Long getNegative();Double getLatency(); }
    @org.springframework.data.jpa.repository.Query("select count(e) as total, coalesce(sum(case when e.fallback=true then 1 else 0 end),0) as fallback, coalesce(sum(case when e.feedback='positive' then 1 else 0 end),0) as positive, coalesce(sum(case when e.feedback='negative' then 1 else 0 end),0) as negative, coalesce(avg(e.latencyMs),0) as latency from ChatEvent e where e.createdAt>=:since")
    Summary summarize(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
    @org.springframework.data.jpa.repository.Query("select e.intent,count(e) from ChatEvent e where e.createdAt>=:since group by e.intent")
    List<Object[]> countIntents(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
    void deleteByCreatedAtBefore(java.time.LocalDateTime cutoff);
}
