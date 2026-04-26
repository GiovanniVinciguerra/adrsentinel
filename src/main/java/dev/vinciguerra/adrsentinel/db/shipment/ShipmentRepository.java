package dev.vinciguerra.adrsentinel.db.shipment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;

/**
 * Data Access Object (DAO) primario per l'entità {@link Shipment}.
 * <p>
 * <b>Ruolo nel Dominio Logistico:</b><br>
 * Gestisce l'accesso al database per l'intero ciclo di vita dei Viaggi/Spedizioni. 
 * Ereditando da {@link JpaRepository}, delega al motore di Spring Data la generazione 
 * automatica e ottimizzata delle query SQL (Query Derivation), garantendo astrazione 
 * dal dialetto specifico del database (PostgreSQL/MySQL).
 * </p>
 * <p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
	/**
	 * Ricerca una spedizione tramite il suo identificatore univoco globale (Lettera di Vettura / Tracking).
	 * <p>
	 * <b>Pattern di Sicurezza (Null-Safety):</b><br>
	 * Ritorna un {@link Optional} per costringere il chiamante (il Service Layer) a gestire 
	 * esplicitamente lo scenario di <i>"Spedizione Non Trovata"</i>, tipicamente lanciando 
	 * una {@code ResourceNotFoundException}, evitando così i pericolosi {@code NullPointerException}.
	 * </p>
	 * @param trackingNumber la Business Key alfanumerica della spedizione.
	 * @return un {@link Optional} contenente l'entità se presente nel DB.
	 */
	Optional<Shipment> findByTrackingNumber(String trackingNumber);
	/**
	 * Recupera l'elenco delle spedizioni la cui partenza è programmata all'interno di uno specifico intervallo temporale.
	 * <p>
	 * <b>Pattern Architetturale (Time-Bounding):</b><br>
	 * Questo metodo rappresenta l'evoluzione operativa della ricerca per data. Sostituendo la fragile 
	 * ricerca esatta al millisecondo con una logica a "finestra temporale" (Range), si garantisce 
	 * che il sistema trovi tutte le spedizioni di un determinato turno o giornata lavorativa, a prescindere 
	 * dalla precisione infinitesimale del timestamp di salvataggio.
	 * </p>
	 * <p>
	 * <b>Design per le Performance (Sargability & Indexing):</b><br>
	 * L'utilizzo della keyword {@code Between} fa sì che Spring Data JPA generi una query SQL ottimizzata: 
	 * {@code WHERE shipment_date >= ? AND shipment_date <= ?}.
	 * Questa struttura sintattica è definita <b>Sargable</b> (<i>Search Argument Able</i>). 
	 * Mantenendo la colonna del database "pulita" (ovvero senza applicarvi funzioni SQL di conversione 
	 * come {@code DATE(shipment_date)} o {@code CAST}), il database è in grado di attraversare direttamente 
	 * l'indice B-Tree associato alla colonna. Questo previene i disastrosi <i>Full Table Scan</i>, 
	 * garantendo latenze di risposta sub-millisecondo anche con tabelle di storici contenenti milioni di record.
	 * </p>
	 * @param startOfDay il limite temporale inferiore (inclusivo) della ricerca (es. l'inizio della giornata alle {@code 00:00:00.000}).
	 * @param endOfDay il limite temporale superiore (inclusivo) della ricerca (es. la fine della giornata alle {@code 23:59:59.999999}).
	 * @return una {@link List} contenente le spedizioni programmate nell'arco temporale richiesto. Se nessuna spedizione ricade nell'intervallo, restituisce una lista vuota.
	 */
	List<Shipment> findByShipmentDateBetween(LocalDateTime startOfDay, LocalDateTime endOfDay);
	/**
	 * Recupera le spedizioni filtrate in base a un determinato stato operativo del loro ciclo di vita.
	 * <p>
	 * <b>Architettura dei Dati (Unbounded Data & Paginazione):</b><br>
	 * Poiché il numero di spedizioni in un determinato stato (specialmente stati storici come {@code DELIVERED}) 
	 * è destinato a crescere indefinitamente nel tempo, questo metodo rigetta l'uso delle classiche collection 
	 * in RAM a favore di un approccio rigorosamente paginato a livello di database. Questo garantisce un'impronta 
	 * di memoria (Heap footprint) piatta e costante O(1), prevenendo rischi di {@code OutOfMemoryError}.
	 * </p>
	 * <p>
	 * <b>Use Case Logistico:</b><br>
	 * Metodo fondamentale per alimentare le <i>Dashboard Operative</i> (es. rendering progressivo su mappa 
	 * di cluster di spedizioni {@code IN_TRANSIT}) o per l'ingestion controllata nei processi Batch 
	 * (es. cron job notturni che elaborano le spedizioni a blocchi, o "chunk", per inviare alert di ritardo).
	 * </p>
	 * @param shipmentStatus lo stato della macchina a stati finiti (State Machine) della spedizione.
	 * @param pageable le direttive di paginazione e ordinamento (es. numero di pagina, dimensione del blocco, sort).
	 * @return un oggetto {@link Page} contenente il blocco di spedizioni richiesto e i metadati di navigazione.
	 */
	Page<Shipment> findByShipmentStatus(ShipmentStatus shipmentStatus, Pageable pageable);
	/**
	 * Esegue l'estrazione dello storico cronologico di tutte le spedizioni (passate e presenti) 
	 * assegnate e processate da uno specifico veicolo della flotta.
	 * <p>
	 * <b>Capacity Planning e Sicurezza (Defensive Programming):</b><br>
	 * Il ciclo di vita operativo di un mezzo pesante può generare decine di migliaia di record di viaggio. 
	 * L'obbligo del parametro {@link Pageable} funge da "valvola di sicurezza", impedendo che la 
	 * richiesta di un registro (Logbook) di un mezzo storico causi colli di bottiglia sul database 
	 * o saturi il Thread locale del server applicativo.
	 * </p>
	 * @param vehicle l'istanza di dominio del veicolo di cui si vuole consultare il registro spedizioni.
	 * @param pageable i criteri strutturati per il caricamento progressivo (lazy/infinite scrolling dei record).
	 * @return un oggetto {@link Page} contenente il sottoinsieme dello storico del veicolo, pronto per l'esposizione.
	 */
	Page<Shipment> findByVehicle(Vehicle vehicle, Pageable pageable);
}
