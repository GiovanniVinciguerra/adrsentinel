package dev.vinciguerra.adrsentinel.db.customer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Interfaccia Repository (Data Access Layer) delegata alla persistenza e al recupero delle entità storiche {@link CustomerSnapshot}.
 * <p><b>Ruolo Architetturale e Isolamento del Manifest:</b></p>
 * Estendendo {@link JpaRepository}, questa interfaccia eredita nativamente le operazioni di I/O verso il database relazionale,
 * fungendo da unico punto di accesso per le fotografie anagrafiche dei clienti associati alle spedizioni. In linea con il
 * pattern architetturale "Append-Only" stabilito per gli snapshot, questo repository viene utilizzato dal Service Layer
 * prevalentemente per operazioni di inserimento massivo (durante la transizione in uscita dallo stato PLANNED) e per
 * l'estrazione in sola lettura del reperto storico, garantendo l'assoluta inalterabilità del log logistico.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface CustomerSnapshotRepository extends JpaRepository<CustomerSnapshot, Long> {
	/**
	 * Recupera l'elenco completo degli snapshot anagrafici (Mittente, Destinatario, Vettore) associati univocamente a una specifica spedizione.
	 * <p><b>Query Derivata (Spring Data JPA) e Traduzione SQL:</b></p>
	 * Sfruttando la naming convention del framework, il metodo attraversa la relazione Many-to-One verso l'entità {@link Shipment}.
	 * A livello di motore ORM (Hibernate), questo costrutto genera ed esegue in modo ottimizzato una query parametrica del tipo:
	 * {@code SELECT * FROM customer_snapshot WHERE shipment_id = ?}.
	 * <p><b>Contesto di Dominio e Ricostruzione Storica:</b></p>
	 * Questo metodo rappresenta lo strumento elaborativo primario tramite cui il sistema, operando come "Router dei Dati",
	 * ricostruisce il perimetro anagrafico per le spedizioni consolidate negli stati operativi o terminali
	 * ({@code TRANSIT}, {@code DELIVERED}, {@code CANCELED}). Il tipo di ritorno è strutturato come {@link List} poiché
	 * una singola spedizione aggrega molteplici attori giuridici, ciascuno rigidamente differenziato dal proprio {@code CustomerRole}.
	 * @param id La Chiave Primaria (Surrogate Key) identificativa della spedizione master ({@link Shipment}).
	 * @return Una lista di entità storiche {@link CustomerSnapshot} contenenti i dati anagrafici congelati. Restituisce una lista vuota 
	 * in assenza di match (es. se la spedizione è ancora in fase PLANNED e non ha generato lo storico).
	 */
	List<CustomerSnapshot> findByShipment_Id(Long id);
}
