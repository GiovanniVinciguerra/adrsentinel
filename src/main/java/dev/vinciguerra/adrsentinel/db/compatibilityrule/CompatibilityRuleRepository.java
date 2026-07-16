package dev.vinciguerra.adrsentinel.db.compatibilityrule;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;

/**
 * Data Access Object (DAO) per la gestione dell'entità {@link CompatibilityRule}.
 * <p>
 * <b>Contesto di Dominio (Logistica ADR):</b><br>
 * Questo repository è il motore di accesso ai dati per le regole del <b>"Carico in Comune" (Mixed Loading)</b>. 
 * Interroga il database per verificare se specifiche classi di merci pericolose (es. Classe 3 - Liquidi Infiammabili 
 * e Classe 8 - Sostanze Corrosive) possono essere fisicamente trasportate sullo stesso veicolo senza 
 * violare le normative internazionali sulla sicurezza.
 * </p>
 * <p>
 * Ereditando da {@link JpaRepository}, beneficia della generazione dinamica delle query (Query Derivation) 
 * gestita dal container di Spring Data.
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface CompatibilityRuleRepository extends JpaRepository<CompatibilityRule, Long> {
	/**
	 * Recupera tutte le regole di compatibilità in cui il classCode della classe ADR specificata figura 
	 * come "Classe A" (Sorgente).
	 * <p>
	 * <b>Meccanica della Query (Spring Data JPA):</b><br>
	 * Il framework analizza il nome del metodo ({@code findBy...}) e genera dinamicamente una query SQL 
	 * che filtra i record della tabella {@code compatibility_rule} utilizzando la Foreign Key 
	 * (es. {@code adr_class_a_id}) associata all'entità passata come parametro.
	 * </p>
	 * @param adrClassCodeA il classCode dell'istanza {@link AdrClass} che rappresenta la prima classe di pericolo da analizzare.
	 * @return una {@link List} contenente tutte le regole di compatibilità applicabili alla classe specificata. 
	 * Restituisce una lista vuota se la classe non ha regole censite nel sistema.
	 */
	List<CompatibilityRule> findByAdrClassA_ClassCode(String adrClassCodeA);
	/**
	 * Verifica l'esistenza di una regola di compatibilità tra due classi ADR
	 * identificate dai rispettivi {@code classCode}.
	 * <p>
	 * Il metodo utilizza una query derivata di Spring Data JPA per verificare se
	 * nel database è presente almeno una {@link CompatibilityRule} avente:
	 * </p>
	 * <ul>
	 *   <li>{@code adrClassA.classCode = adrClassCodeA};</li>
	 *   <li>{@code adrClassB.classCode = adrClassCodeB}.</li>
	 * </ul>
	 * <h3>Importante: ordinamento canonico dei parametri</h3>
	 * <p>
	 * La {@link CompatibilityRule} impone che le due classi ADR vengano salvate
	 * secondo un ordine canonico (la classe con {@code classCode}
	 * alfanumericamente minore viene sempre memorizzata come
	 * {@code adrClassA}, mentre quella maggiore come {@code adrClassB}).
	 * Di conseguenza questo metodo <strong>non esegue una ricerca
	 * bidirezionale</strong>.
	 * </p>
	 * <p>
	 * Ad esempio, se nel database è presente la regola:
	 * </p>
	 * <pre>
	 * adrClassA.classCode = "3"
	 * adrClassB.classCode = "8"
	 * </pre>
	 * <p>
	 * allora:
	 * </p>
	 * <pre>
	 * existsByAdrClassA_ClassCodeAndAdrClassB_ClassCode("3", "8") // true
	 * existsByAdrClassA_ClassCodeAndAdrClassB_ClassCode("8", "3") // false
	 * </pre>
	 * <p>
	 * È pertanto stato necessario utilizzare il metodo 
	 * {@link CompatibilityRule#safeOrderForUniqueConstraint} per ovviare a questo problema.
	 * </p>
	 * <h3>Utilizzo tipico</h3>
	 * <p>
	 * Questo metodo viene normalmente impiegato come controllo preventivo prima
	 * della persistenza di una nuova regola di compatibilità, al fine di fornire
	 * un messaggio di errore più descrittivo in caso di duplicato.
	 * Tale verifica, tuttavia, <strong>non sostituisce</strong> il vincolo di
	 * unicità definito a livello di database
	 * ({@code uk_class_a_class_b}), che rimane l'unico meccanismo in grado di
	 * garantire l'assenza di duplicati anche in presenza di transazioni
	 * concorrenti.
	 * </p>
	 * @param adrClassCodeA il {@code classCode} della prima classe ADR,
	 * corrispondente alla proprietà {@code adrClassA}.
	 * @param adrClassCodeB il {@code classCode} della seconda classe ADR,
	 * corrispondente alla proprietà {@code adrClassB}.
	 * @return {@code true} se esiste almeno una regola di compatibilità avente
	 * esattamente la coppia di {@code classCode} specificata; {@code false} in 
	 * caso contrario.
	 */
	boolean existsByAdrClassA_ClassCodeAndAdrClassB_ClassCode(String adrClassCodeA, String adrClassCodeB);
}
