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
	 * Recupera tutte le regole di compatibilità in cui la classe ADR specificata figura come "Classe A" (Sorgente).
	 * <p>
	 * <b>Meccanica della Query (Spring Data JPA):</b><br>
	 * Il framework analizza il nome del metodo ({@code findBy...}) e genera dinamicamente una query SQL 
	 * che filtra i record della tabella {@code compatibility_rule} utilizzando la Foreign Key (es. {@code adr_class_a_id}) 
	 * associata all'entità passata come parametro.
	 * </p>
	 * @param adrClassA l'istanza completa dell'entità {@link AdrClass} che rappresenta la prima classe di pericolo da analizzare.
	 * @return una {@link List} contenente tutte le regole di compatibilità applicabili alla classe specificata. 
	 * Restituisce una lista vuota se la classe non ha regole censite nel sistema.
	 */
	List<CompatibilityRule> findByAdrClassA(AdrClass adrClassA);
}
