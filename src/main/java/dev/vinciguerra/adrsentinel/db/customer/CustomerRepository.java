package dev.vinciguerra.adrsentinel.db.customer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Livello di Accesso ai Dati (Data Access Layer) per l'entità {@link Customer}.
 * <p>
 * Questo repository funge da interfaccia tra la logica di business (Service Layer) e il database relazionale.
 * Estendendo {@link JpaRepository}, eredita nativamente tutte le operazioni standard di CRUD, paginazione 
 * e flush del contesto di persistenza, senza la necessità di implementare codice boilerplate (es. query SQL manuali).
 * </p>
 * <p>
 * <b>Ruolo nel Domain-Driven Design (DDD):</b><br>
 * Nel contesto di AdrSentinel, il {@code Customer} agisce come <i>Aggregate Root</i> per l'anagrafica aziendale. 
 * Questo repository è l'unico punto di ingresso autorizzato per interrogare e persistere le identità legali 
 * (Mittenti, Destinatari, Vettori) che prenderanno parte alle spedizioni.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
	/**
	 * Recupera l'anagrafica di un cliente effettuando una ricerca esatta basata sulla sua chiave di business 
	 * (la Partita IVA o il Codice Fiscale).
	 * <p>
	 * Essendo la colonna {@code vat_number} protetta da un vincolo di unicità ({@code @UniqueConstraint}) 
	 * a livello di database, questa query restituirà sempre al massimo un singolo record. 
	 * È il metodo primario da utilizzare durante le validazioni di inserimento per prevenire la 
	 * duplicazione delle anagrafiche.
	 * </p>
	 * @param vatNumber La Partita IVA esatta da cercare. <b>Nota Architetturale:</b> si presuppone che 
	 * la stringa passata a questo metodo sia già stata normalizzata (rimozione spazi, 
	 * forzatura al maiuscolo) per corrispondere al formato salvato dall'hook 
	 * {@code @PrePersist} dell'entità.
	 * @return Un {@link Optional} contenente l'entità {@code Customer} se trovata, altrimenti {@link Optional#empty()}. 
	 * L'utilizzo di Optional forza il programmatore a gestire esplicitamente l'assenza del dato, 
	 * prevenendo {@code NullPointerException}.
	 */
	Optional<Customer> findByVatNumber(String vatNumber);
	/**
	 * Esegue una ricerca parziale e *Case-Insensitive* sulla Ragione Sociale delle aziende registrate.
	 * <p>
	 * <b>Traduzione SQL:</b> {@code SELECT * FROM customer WHERE UPPER(company_name) LIKE UPPER('%?%')}
	 * </p>
	 * <p>
	 * <b>Caso d'uso (UX/UI):</b><br>
	 * Questo metodo è progettato specificamente per alimentare i componenti di <b>Autocomplete</b> 
	 * o le barre di ricerca dinamiche sul Front-End. Permette agli operatori logistici di trovare 
	 * un'azienda digitando solo una frazione del nome (es. "logist" troverà "Logistica Srl", 
	 * "Pippo LOGISTICA", ecc.), indipendentemente dalle maiuscole e minuscole.
	 * </p>
	 * <p>
	 * Restituisce una {@link List} poiché la colonna {@code company_name} non è univoca e potrebbero 
	 * esistere molteplici aziende omonime o con nomi simili (gestione delle omonimie).
	 * </p>
	 * @param companyName Il frammento di testo (substring) da ricercare all'interno della Ragione Sociale.
	 * @return Una lista di entità {@code Customer} che contengono la stringa cercata. Restituisce una 
	 * lista vuota (e mai {@code null}) se nessuna corrispondenza viene trovata.
	 */
	List<Customer> findByCompanyNameContainingIgnoreCase(String companyName);
}
