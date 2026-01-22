package com.datagami.edudron.content.repo;

import com.datagami.edudron.common.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommonEventRepository extends JpaRepository<Event, String> {
    // All query methods are inherited from JpaRepository
    // Events are stored in common.events table
}
