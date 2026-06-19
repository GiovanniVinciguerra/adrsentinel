package dev.vinciguerra.adrsentinel.db.driver;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository per l'accesso ai dati e la gestione del ciclo di vita dell'entità {@link Driver}.
 * * <p>
 * Questa interfaccia estende {@link JpaRepository}, ereditando automaticamente l'implementazione
 * dei metodi standard per le operazioni CRUD (Create, Read, Update, Delete), nonché funzionalità
 * avanzate per l'elaborazione in batch, la paginazione e l'ordinamento dei dati nel database.
 * </p>
 * * <p>
 * L'annotazione {@link Repository} indica al framework Spring che questo componente
 * agisce come un Data Access Object (DAO). Grazie a questa annotazione, Spring rileverà
 * l'interfaccia durante il component scanning, ne fornirà un'implementazione a runtime e si 
 * occuperà di tradurre in modo trasparente le eccezioni native del database (es. quelle sollevate 
 * da Hibernate/JPA) nella gerarchia di eccezioni generiche di Spring ({@code DataAccessException}).
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
	/**
     * Ricerca e recupera un conducente (Driver) in base al suo numero di patente (license).
     * <p>
     * Questo è un "query method" derivato di Spring Data JPA. Spring analizza la firma 
     * e il nome del metodo ({@code findBy...}) per costruire e tradurre automaticamente 
     * la corrispondente query JPQL (ad esempio: {@code SELECT d FROM Driver d WHERE d.license = ?1}) 
     * senza che sia necessario scriverla esplicitamente.
     * </p>
     * <p>
     * Il tipo di ritorno è incapsulato in un {@link java.util.Optional} per garantire 
     * un approccio "null-safe" (sicuro contro i valori nulli). Se la query non produce 
     * alcun risultato nel database, il metodo restituirà elegantemente un {@code Optional.empty()} 
     * anziché un riferimento {@code null}, prevenendo così il rischio di {@link NullPointerException} 
     * a livello di business logic.
     * </p>
     * @param license La stringa alfanumerica che rappresenta la patente univoca del conducente. 
     * Si presuppone che questo valore sia unico all'interno del database.
     * @return Un oggetto {@link Optional} contenente l'istanza di {@link Driver} se trovata, 
     * oppure un {@link Optional#empty()} se non esiste alcun conducente associato 
     * alla patente fornita.
     */
	Optional<Driver> findByLicense(String license);
}
