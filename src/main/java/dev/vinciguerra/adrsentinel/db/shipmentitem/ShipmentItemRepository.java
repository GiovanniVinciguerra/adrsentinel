package dev.vinciguerra.adrsentinel.db.shipmentitem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;

/**
 * Livello di accesso ai dati (Data Access Object - DAO) per l'entità {@link ShipmentItem}.
 * <p><b>Contesto Architetturale (Repository Pattern):</b></p>
 * Questa interfaccia astrae la complessità dell'infrastruttura di persistenza (JPA/Hibernate) 
 * dal livello di logica di business (Service). Estendendo {@link JpaRepository}, eredita 
 * automaticamente un set completo di operazioni CRUD, meccanismi di paginazione e ordinamento, 
 * oltre a beneficiare della traduzione automatica delle eccezioni SQL in eccezioni 
 * standard della gerarchia Spring ({@code DataAccessException}).
 * <p><b>Generazione Dinamica:</b></p>
 * Sfruttando il motore di Query Derivation di Spring Data, l'implementazione concreta 
 * di questa interfaccia viene generata dinamicamente a runtime tramite proxy, senza 
 * la necessità di scrivere codice boilerplate o query JPQL manuali.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, Long> {
	/**
	 * Esegue una ricerca puntuale (Lookup) di un singolo articolo logistico utilizzando 
	 * la sua Business Key primaria (UUID).
	 * <p><b>Design Pattern e Sicurezza (Null-Safety):</b></p>
	 * Il ritorno è avvolto in un costrutto {@link Optional}. Questa è una pratica architetturale 
	 * essenziale che forza il livello chiamante (Service) a gestire in modo esplicito 
	 * il caso in cui l'articolo non sia presente nel database, prevenendo radicalmente 
	 * le {@code NullPointerException}. Tipicamente, il Service invocherà un 
	 * {@code .orElseThrow(() -> new ResourceNotFoundException(...))} su questo risultato.
	 * <p><b>Nota sulle Performance:</b></p>
	 * Essendo l'UUID un identificatore di ricerca frequente, è altamente raccomandato 
	 * che il campo {@code itemUUID} possieda un indice univoco (Unique Constraint/Index) 
	 * a livello di database per garantire tempi di risoluzione O(1) o O(log n).
	 * @param itemUUID L'identificatore univoco universale (Universal Unique Identifier) dell'articolo. 
	 * Garantisce l'unicità attraverso sistemi distribuiti e non dipende dalle 
	 * sequenze del database.
	 * @return Un {@link Optional} contenente l'istanza di {@link ShipmentItem} se trovata, 
	 * altrimenti un {@link Optional#empty()}.
	 */
	Optional<ShipmentItem> findByItemUUID(String itemUUID);
	/**
	 * Estrae l'intera collezione di articoli (Items) associati a una specifica spedizione "Padre".
	 * <p><b>Risoluzione della Relazione (Parent-Child):</b></p>
	 * Questa query interroga la chiave esterna (Foreign Key) presente nella tabella 
	 * degli articoli che punta alla tabella delle spedizioni. Invece di passare 
	 * un ID scalare (es. {@code Long shipmentId}), il metodo accetta l'intera entità 
	 * {@link Shipment}. Hibernate estrarrà in automatico l'ID dall'oggetto Padre per 
	 * costruire la clausola {@code WHERE} della query SQL sottostante.
	 * <p><b>Casi d'Uso Tipici:</b></p>
	 * <ul>
	 * <li>Calcolo aggregato del volume/peso totale di una spedizione.</li>
	 * <li>Rendering della distinta di carico (Manifest) per il corriere.</li>
	 * <li>Esecuzione di operazioni di "Cascading" manuale prima di un'eliminazione.</li>
	 * </ul>
	 * @param shipmentTrackingNumber Il numero di tracking della spedizione padre di cui si vogliono recuperare 
	 * gli articoli figli.
	 * @return Una {@link List} contenente tutti i {@link ShipmentItem} collegati alla spedizione. 
	 * Ritorna una lista vuota (e mai {@code null}) qualora la spedizione non contenga alcun articolo.
	 */
	List<ShipmentItem> findByShipmentTrackingNumber(String shipmentTrackingNumber);
}
