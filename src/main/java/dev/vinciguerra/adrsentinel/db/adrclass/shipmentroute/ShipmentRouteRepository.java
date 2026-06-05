package dev.vinciguerra.adrsentinel.db.adrclass.shipmentroute;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Interfaccia del livello di persistenza (Persistence Layer / Data Access Object) 
 * deputata alla gestione del ciclo di vita dell'entità di dominio {@link ShipmentRoute} 
 * all'interno del database relazionale.
 * <p>
 * <b>Contesto Architetturale (Repository Pattern):</b><br>
 * In conformità ai principi del Domain-Driven Design (DDD), questa interfaccia astrae 
 * completamente la complessità di interazione con il database (Hibernate / EntityManager). 
 * Fornisce al Service Layer un set di operazioni CRUD (Create, Read, Update, Delete) 
 * out-of-the-box e garantisce che le transazioni siano gestite in modo sicuro dal 
 * proxy generato dinamicamente da Spring Boot a runtime.
 * </p>
 * <p>
 * <b>Estensione JpaRepository:</b><br>
 * Ereditando da {@code JpaRepository<ShipmentRoute, Long>}, il repository eredita non solo 
 * i metodi CRUD standard, ma anche funzionalità avanzate per la paginazione (Pagination) 
 * e l'ordinamento (Sorting), oltre al flushing manuale del contesto di persistenza (JPA Session).
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface ShipmentRouteRepository extends JpaRepository<ShipmentRoute, Long> {
	/**
	 * Recupera una singola rotta logistica ricercandola tramite il suo identificativo pubblico univoco.
	 * <p>
	 * <b>Meccanismo Interno (Query Derivation):</b><br>
	 * L'implementazione SQL di questo metodo non necessita di annotazioni {@code @Query}. 
	 * Il parser sintattico di Spring Data JPA analizza il nome del metodo ({@code findBy...}) 
	 * e genera automaticamente la clausola {@code WHERE route_uuid = ?} al momento dell'avvio 
	 * dell'Application Context.
	 * </p>
	 * <p>
	 * <b>Nota di Sicurezza (Anti-IDOR Strategy):</b><br>
	 * Dal punto di vista della sicurezza (OWASP), è prassi vietata esporre e ricercare risorse 
	 * API tramite la Primary Key sequenziale del database (il campo {@code Long id}). 
	 * Questo metodo implementa una ricerca basata su UUID (Universally Unique Identifier), 
	 * un valore alfanumerico ad alta entropia che previene attacchi di tipo 
	 * Insecure Direct Object Reference (Enumerazione e furto di risorse altrui).
	 * </p>
	 * <p>
	 * <b>Programmazione Difensiva (Null-Safety):</b><br>
	 * Il tipo di ritorno è incapsulato in un {@link Optional}. Questo costringe esplicitamente 
	 * lo sviluppatore (nel Service Layer) a gestire lo scenario in cui l'UUID fornito dal client 
	 * non esista nel database, eliminando alla radice il rischio di {@code NullPointerException}.
	 * </p>
	 * @param routeUUID L'identificatore pubblico a 36 caratteri (formato standard UUIDv4) 
	 * generato al momento della creazione della rotta (es. "550e8400-e29b-41d4-a716-446655440000").
	 * @return Un {@link Optional} contenente l'entità {@link ShipmentRoute} popolata se trovata; 
	 * {@link Optional#empty()} se nessun record nel database corrisponde all'UUID passato.
	 */
	Optional<ShipmentRoute> findByRouteUUID(String routeUUID);
	/**
	 * Recupera l'intera sequenza di segmenti stradali (Legs/Tratte) associati a una specifica 
	 * spedizione, interrogando il database tramite l'identificativo pubblico univoco (Tracking Number).
	 * <p>
	 * <b>Contesto Architetturale (Multi-Stop Routing):</b><br>
	 * A seguito dell'evoluzione del motore di calcolo verso il routing a tappe multiple, una singola 
	 * entità {@code Shipment} non è più legata a una singola rotta monolitica, ma a una collezione di 
	 * segmenti intermedi. Per questo motivo, il metodo è progettato per restituire una {@code List<ShipmentRoute>}, 
	 * rappresentante l'intero viaggio frazionato che il veicolo dovrà compiere.
	 * </p>
	 * <p>
	 * <b>Meccanismo Spring Data JPA (Property Traversal & Query Derivation):</b><br>
	 * Questo metodo sfrutta la potente funzionalità di "Property Traversal" di Spring Data. 
	 * Analizzando la firma del metodo ({@code findBy} + {@code Shipment} + {@code TrackingNumber}), 
	 * il framework genera automaticamente a runtime una query SQL ottimizzata che esegue un 
	 * {@code INNER JOIN} implicito tra la tabella {@code shipment_route} e la tabella padre {@code shipment}. 
	 * Questo permette di filtrare le rotte usando un attributo dell'entità relazionata senza dover 
	 * scrivere query JPQL custom con l'annotazione {@code @Query}.
	 * </p>
	 * <p>
	 * <b>Sicurezza (Anti-IDOR Policy):</b><br>
	 * L'utilizzo del {@code trackingNumber} (formato UUID) come criterio di ricerca al posto 
	 * del classico {@code shipmentId} (Primary Key sequenziale) garantisce che l'esposizione 
	 * di questo metodo nei layer superiori non presti il fianco ad attacchi di tipo 
	 * <i>Insecure Direct Object Reference</i> o all'enumerazione dei record da parte di client malevoli.
	 * </p>
	 * @param shipmentTrackingNumber L'identificativo di sicurezza alfanumerico (UUID) che 
	 * identifica univocamente la spedizione padre nel sistema.
	 * @return Una {@link List} contenente le entità {@link ShipmentRoute} associate alla spedizione. 
	 * <br><b>Nota Null-Safety:</b> In conformità alle specifiche di Spring Data, se nessuna 
	 * rotta viene trovata per il tracking number fornito, il metodo restituisce sempre 
	 * una lista vuota ({@code []}) e <b>mai</b> {@code null}, prevenendo {@code NullPointerException} 
	 * nel Service Layer.
	 */
	List<ShipmentRoute> findByShipmentTrackingNumber(String shipmentTrackingNumber);
}
