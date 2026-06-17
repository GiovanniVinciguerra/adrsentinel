package dev.vinciguerra.adrsentinel.db.driver;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
	Optional<Driver> findByLicense(String license);
}
