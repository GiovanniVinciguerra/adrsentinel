package dev.vinciguerra.adrsentinel.db.vehicle;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Data Access Object (DAO) per l'entità {@link Vehicle}.
 * <p>
 * Sfrutta il motore di Spring Data JPA per generare automaticamente le query SQL a runtime.
 * Estendendo {@link JpaRepository}, eredita nativamente tutte le operazioni CRUD (Create, Read, Update, Delete), 
 * la paginazione e l'ordinamento dei risultati.
 * </p>
 * <p>
 * <b>Sicurezza Architetturale:</b> Questa interfaccia agisce come strato di isolamento tra la logica di business 
 * (i Service) e il database fisico, garantendo che le transazioni e le connessioni vengano gestite 
 * in modo sicuro dal container IoC di Spring.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
	/**
	 * Ricerca un singolo veicolo utilizzando la sua <b>Business Key</b> (la targa).
	 * <p>
	 * Dal momento che la colonna {@code license_plate} ha un vincolo {@code UNIQUE} nel database, 
	 * questa query restituirà sempre al massimo un singolo record.
	 * </p>
	 * <p>
	 * <b>Pattern Design:</b> Il ritorno è incapsulato in un {@link Optional}. Questo costringe esplicitamente 
	 * lo sviluppatore (a livello di Service) a gestire in modo sicuro l'eventualità che il veicolo non esista, 
	 * tipicamente utilizzando {@code .orElseThrow(() -> new ResourceNotFoundException(...))}.
	 * </p>
	 * @param licensePlate la targa del veicolo da cercare (normalizzata in uppercase senza spazi).
	 * @return un {@link Optional} contenente il veicolo se trovato, oppure un Optional vuoto.
	 */
	Optional<Vehicle> findByLicensePlate(String licensePlate);
	/**
	 * Ricerca tutti i veicoli attivi ({@code isActive() == true}) che hanno una portata utile <b>almeno uguale</b> a quella specificata.
	 * <p>
	 * <b>Nota Architetturale:</b> Il parametro è stato allineato al tipo wrapper {@link Integer} per 
	 * coerenza con il mapping dell'entità {@code Vehicle}.
	 * </p>
	 * @param maxUsefulWeightkg il peso massimo utile (portata) esatto in chilogrammi.
	 * @return una {@link List} di veicoli che corrispondono al criterio, o una lista vuota se nessun veicolo soddisfa il requisito.
	 */
	List<Vehicle> findByMaxUsefulWeightkgGreaterThanEqual(Integer maxUsefulWeightkg);
}
