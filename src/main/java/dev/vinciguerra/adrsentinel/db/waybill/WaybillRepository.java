package dev.vinciguerra.adrsentinel.db.waybill;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Interfaccia di accesso ai dati (Repository Layer) per l'entità {@link Waybill}.
 * <p><b>Contesto Architetturale (Data Access Object):</b></p>
 * Questo componente funge da ponte tra la logica di business (Service Layer) e il database 
 * relazionale, incapsulando l'interazione con la tabella dei Documenti di Trasporto (D.D.T.). 
 * Estendendo {@code JpaRepository}, eredita nativamente tutte le operazioni CRUD standard, 
 * la paginazione e le funzionalità di flush del contesto di persistenza fornite da Hibernate.
 * <p><b>Gestione dei Blob e Performance:</b></p>
 * Poiché l'entità {@code Waybill} ospita un payload binario pesante (il file PDF mappato 
 * come {@code LONGBLOB}), le query derivate in questa interfaccia sono state strategicamente 
 * disegnate per minimizzare l'impronta in memoria (Memory Footprint) durante i controlli logici.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface WaybillRepository extends JpaRepository<Waybill, Long> {
	/**
	 * Recupera in modo sicuro il Documento di Trasporto associato a una specifica spedizione.
	 * <p><b>Risoluzione del Naming (Property Traversal):</b></p>
	 * L'uso dell'underscore ({@code _}) nel nome del metodo istruisce esplicitamente il motore 
	 * di Spring Data JPA a navigare in sicurezza la relazione: parte dall'entità {@code Waybill}, 
	 * entra nell'oggetto incassato {@code shipment} e ne estrae la proprietà {@code id}.
	 * @param id L'identificativo primario (Surrogate Key) dell'entità logistica {@code Shipment}.
	 * @return Un contenitore {@link Optional} provvisto del {@code Waybill} qualora il documento 
	 * sia già stato generato, oppure {@code Optional.empty()} se la spedizione è ancora 
	 * sprovvista di D.D.T. L'uso dell'Optional obbliga il Service Layer a gestire il caso 
	 * di assenza (Null-Safety) prevenendo eccezioni a runtime.
	 */
	Optional<Waybill> findByShipment_Id(Long id);
	/**
	 * Estrae esclusivamente i metadati scalari del Documento di Trasporto (D.D.T.) 
	 * associato a una specifica spedizione, omettendo intenzionalmente il payload binario.
	 * <p><b>Contesto Architetturale (Memory Optimization &amp; Projection):</b></p>
	 * Questa query JPQL implementa un pattern di ottimizzazione critico per la scalabilità 
	 * del sistema. Sfruttando la <i>Constructor Expression</i> ({@code SELECT new...}), 
	 * il framework istanzia un oggetto {@link Waybill} popolato unicamente con le 
	 * informazioni anagrafiche, lasciando il campo {@code pdfData} (LONGBLOB) a {@code null}. 
	 * Questo previene colli di bottiglia e potenziali {@code OutOfMemoryError}, evitando 
	 * di scaricare e allocare nella Heap Memory della JVM svariati Megabyte di file PDF 
	 * quando il Business Layer necessita solo di leggere i dati del documento.
	 * <p><b>Dettagli Implementativi (Entity State &amp; Parameter Binding):</b></p>
	 * <ul>
	 * <li><i>Detached State:</i> Poiché l'entità è istanziata tramite operatore {@code new}, 
	 * l'oggetto restituito nasce in stato <b>Detached</b>. Non essendo monitorato dal 
	 * <i>Persistence Context</i> di Hibernate, risulta immune da operazioni di 
	 * <i>Dirty Checking</i>. Ciò garantisce che alla chiusura della transazione non 
	 * scatti alcun {@code UPDATE} accidentale che sovrascriverebbe il PDF sul database con un valore nullo.</li>
	 * <li><i>Implicit Binding:</i> Sfruttando le direttive di compilazione moderne (Java 8+), 
	 * il parametro JPQL {@code :id} viene mappato nativamente sulla variabile di firma {@code Long id} 
	 * tramite reflection, permettendo un codice più pulito privo dell'annotazione testuale {@code @Param}.</li>
	 * </ul>
	 * @param id L'identificativo primario interno (Surrogate Key) dell'entità logistica {@code Shipment} 
	 * a cui il documento è vincolato.
	 * @return Un contenitore {@link Optional} provvisto dell'entità {@code Waybill} alleggerita 
	 * qualora il D.D.T. sia stato precedentemente consolidato, oppure {@code Optional.empty()} 
	 * se assente.
	 */
	@Query("""
		SELECT new dev.vinciguerra.adrsentinel.db.waybill.Waybill(
			w.ddtNumber,
			w.filename,
			w.contentType,
			w.createdAt
		)
		FROM Waybill w
		WHERE w.shipment.id = :id
	""")
	Optional<Waybill> findMetadataByShipment_Id(Long id);
	/**
	 * Verifica l'esistenza di un Documento di Trasporto per una determinata spedizione 
	 * senza scaricare il file dal database.
	 * <p><b>Ottimizzazione Architetturale (Query Projection):</b></p>
	 * Questa firma rappresenta una macro-ottimizzazione per le performance. Invece di lanciare 
	 * una gravosa operazione di {@code SELECT *} che costringerebbe il database a trasferire 
	 * svariati Megabyte di dati binari (il {@code pdfData}) nella RAM della Java Virtual Machine, 
	 * Spring Data JPA traduce questo metodo in una query SQL ultraleggera: 
	 * {@code SELECT 1 FROM waybill WHERE shipment_id = ? LIMIT 1}.
	 * <p>
	 * È lo strumento ideale per le validazioni pre-generazione (es. bloccare un client che 
	 * tenta di generare due volte il D.D.T. per la stessa spedizione).
	 * @param id L'identificativo primario dell'entità {@code Shipment}.
	 * @return {@code true} se la spedizione possiede già un documento consolidato a sistema, 
	 * {@code false} altrimenti.
	 */
	boolean existsByShipment_Id(Long id);
}
