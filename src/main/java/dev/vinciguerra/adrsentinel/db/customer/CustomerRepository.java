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
	 * Esegue una ricerca per corrispondenza esatta (Exact Match) sulla Ragione Sociale delle aziende registrate.
	 * <p><b>Traduzione SQL:</b> {@code SELECT * FROM customer WHERE company_name = ?}</p>
	 * <p><b>Contesto Architetturale e Caching:</b></p>
	 * Abbandonando la ricerca parziale (LIKE) in favore del match esatto, questo metodo diventa strutturalmente
	 * idoneo per l'applicazione di policy di <b>Caching</b>. La chiave di cache diviene deterministica e finita,
	 * abbattendo il rischio di "Cache Explosion" tipico delle ricerche testuali parziali e rendendo triviale
	 * la logica di invalidation (Key Shifting) in caso di mutazioni anagrafiche.
	 * <p><b>Domain-Driven Design (DDD) e Gestione delle Omonimie:</b></p>
	 * Il tipo di ritorno è intenzionalmente una {@link List} (e non un {@code Optional}). Nel dominio logistico
	 * e societario reale, la <i>Business Key</i> univoca assoluta è la Partita IVA ({@code vatNumber}).
	 * È lecito e frequente avere a database molteplici entità giuridiche con l'esatta medesima ragione sociale
	 * (es. omonimie territoriali o filiali di uno stesso gruppo societario con identificatori fiscali distinti).
	 * Restituendo una collezione, il sistema previene crash a runtime (es. {@code NonUniqueResultException}
	 * sollevate da Hibernate) demandando la discriminazione finale alla logica di business o all'operatore.
	 * @param companyName La stringa esatta (case-sensitive, in base al dialetto del DB) rappresentante la Ragione Sociale.
	 * @return Una lista di entità {@link Customer} che corrispondono esattamente al nome fornito. Restituisce una lista 
	 * vuota (mai {@code null}) se nessuna corrispondenza viene trovata.
	 */
	List<Customer> findByCompanyName(String companyName);
	/**
	 * Verifica l'esistenza di almeno un'entità avente il numero di partita IVA specificato.
	 * <p>
	 * Questo metodo sfrutta la query derivata di Spring Data JPA basata sul nome del metodo
	 * ({@code existsByVatNumber}) per eseguire una verifica di esistenza senza recuperare
	 * l'intera entità dal database, risultando generalmente più efficiente rispetto
	 * all'utilizzo di metodi che caricano l'oggetto completo.
	 * </p>
	 * <p>
	 * Il confronto viene effettuato sul valore del campo {@code vatNumber} dell'entità
	 * gestita dal repository.
	 * </p>
	 * <h2>Comportamento</h2>
	 * <ul>
	 * <li>Restituisce {@code true} se esiste almeno un record con la partita IVA indicata.</li>
	 * <li>Restituisce {@code false} se nessun record possiede la partita IVA specificata.</li>
	 * </ul>
	 * <h2>Note</h2>
	 * <ul>
	 * <li>Il comportamento in caso di valore {@code null} dipende dalla configurazione
     * del provider JPA e dal mapping dell'entità. È consigliato invocare il metodo
     * con un valore non {@code null}.</li>
     * <li>Qualora il campo {@code vatNumber} sia vincolato come univoco nel database,
     * il metodo verifica semplicemente l'esistenza dell'unico record corrispondente.</li>
	 * </ul>
	 * @param vatNumber la partita IVA da verificare; dovrebbe essere un valore non
	 * {@code null} e conforme al formato previsto dall'applicazione.
	 * @return {@code true} se esiste almeno un'entità con la partita IVA specificata;
	 * {@code false} altrimenti.
	 */
	boolean existsByVatNumber(String vatNumber);
}
