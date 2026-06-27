package dev.vinciguerra.adrsentinel.db.driver;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Interfaccia di Data Access Layer (DAO) per la gestione della persistenza dell'entità {@link DriverSnapshot}.
 * * <p><b>Ruolo Architetturale e Limitazioni Operative:</b></p>
 * Estendendo {@link JpaRepository}, la classe delega a Spring Data JPA l'implementazione a runtime 
 * (tramite proxy) delle operazioni di interazione col database. 
 * Dato che {@link DriverSnapshot} rappresenta un record storico intrinsecamente <b>immutabile</b> 
 * (Write-Once Record), l'utilizzo architetturalmente corretto di questa repository si limita a:
 * <ul>
 * <li><b>Creazione (Insert):</b> Persistenza del record generato al momento dell'avvio della spedizione.</li>
 * <li><b>Lettura (Select):</b> Consultazione storica o estrazione dati per ripristino stato (es. fine transito).</li>
 * </ul>
 * <i>Nota di Design:</i> Operazioni standard ereditate come {@code save()} invocate su entità preesistenti (Update) 
 * o i metodi di cancellazione ({@code delete()}) dovrebbero essere evitati o bloccati dal Service Layer per 
 * non violare il vincolo di inalterabilità della "Fotografia Legale".
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface DriverSnapshotRepository extends JpaRepository<DriverSnapshot, Long> {
	/**
	 * Recupera la fotografia legale (Snapshot) degli autisti interrogando il database 
	 * tramite l'ID della spedizione ({@code Shipment}) a cui lo snapshot è vincolato.
	 * <p><b>Meccanismo di Querying (Property Traversal):</b></p>
	 * Il metodo sfrutta il meccanismo di derivazione delle query di Spring Data. 
	 * L'utilizzo mirato del carattere underscore ({@code _}) nel nome del metodo ({@code Shipment_Id}) 
	 * forza esplicitamente il parser a navigare la proprietà relazionale {@code shipment} all'interno 
	 * di {@code DriverSnapshot} e ad applicare la clausola {@code WHERE} sul campo {@code id} dell'entità 
	 * target. Questo previene ambiguità di risoluzione qualora l'entità principale possedesse un campo 
	 * nominato in modo simile.
	 * @param id L'identificativo primario (Primary Key) della spedizione di cui si ricerca lo snapshot veicolare.
	 * @return Un contenitore {@link List} che contiene gli snapshot dei Driver legati all'ID della spedizione.
	 */
	List<DriverSnapshot> findByShipment_Id(Long id);
}
