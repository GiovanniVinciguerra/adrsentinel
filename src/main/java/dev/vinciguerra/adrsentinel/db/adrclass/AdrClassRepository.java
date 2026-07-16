package dev.vinciguerra.adrsentinel.db.adrclass;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Data Access Object (DAO) per l'entità {@link AdrClass}.
 * <p>
 * Sfrutta la potenza di Spring Data JPA per fornire automaticamente l'implementazione 
 * a runtime di tutte le operazioni standard di CRUD (Create, Read, Update, Delete) 
 * e di Paginazione/Ordinamento, estendendo {@link JpaRepository}.
 * </p>
 * <h3>Nota Architetturale sul Layering:</h3>
 * <ul>
 * <li>Questo layer (Repository) è responsabile esclusivamente dell'interazione con PostgreSQL.</li>
 * <li><b>Non contiene logica di business</b> e non gestisce la Cache, responsabilità delegate al layer Service ({@code AdrClassService}).</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface AdrClassRepository extends JpaRepository<AdrClass, Long> {
	/**
     * Ricerca un'istanza di {@link AdrClass} utilizzando la sua Business Key univoca.
     * <p>
     * Questo metodo sfrutta il meccanismo di <i>Query Derivation</i> di Spring Data: 
     * il framework analizza il nome del metodo ("findBy" + "ClassCode") e genera 
     * automaticamente la query SQL equivalente: 
     * {@code SELECT * FROM adr_class WHERE class_code = ?}
     * </p>
     * <h3>Perché Optional?</h3>
     * Restituisce un {@link java.util.Optional} per abbracciare il paradigma del <i>Null-Safety</i>. 
     * A livello semantico, un'operazione di "ricerca" (find) non garantisce la presenza del dato.
     * Sarà compito del layer Service "scartare" l'Optional e lanciare l'eventuale eccezione (es. {@code ResourceNotFoundException}) 
     * nel caso in cui il contratto richieda la presenza obbligatoria del record.
     *
     * @param classCode Il codice identificativo univoco della classe ADR (es. "3", "8").
     * @return Un {@code Optional} contenente l'entità trovata, oppure {@code Optional.empty()} se nessun record corrisponde al codice.
     */
	Optional<AdrClass> findByClassCode(String classCode);
	/**
	 * Verifica l'esistenza di almeno una {@link AdrClass} avente il
	 * {@code classCode} specificato.
	 * <p>
	 * Il metodo utilizza una query derivata di Spring Data JPA per effettuare un
	 * controllo di esistenza sul database senza recuperare l'intera entità,
	 * risultando generalmente più efficiente rispetto ad una ricerca mediante
	 * {@code findBy...} quando è necessario conoscere esclusivamente la presenza
	 * o l'assenza del record.
	 * </p>
	 * <h3>Comportamento</h3>
	 * <p>
	 * La verifica viene eseguita confrontando il valore della proprietà
	 * {@code classCode} dell'entità {@link AdrClass} con il parametro fornito.
	 * Se almeno un record soddisfa il criterio di ricerca, il metodo restituisce
	 * {@code true}; in caso contrario restituisce {@code false}.
	 * </p>
	 * <h3>Utilizzo tipico</h3>
	 * <p>
	 * Questo metodo è normalmente impiegato come controllo preventivo prima della
	 * creazione di una nuova {@link AdrClass}, al fine di intercettare eventuali
	 * duplicati e fornire un messaggio di errore più descrittivo all'utente.
	 * </p>
	 * <p>
	 * Tale verifica costituisce esclusivamente un controllo applicativo e
	 * <strong>non sostituisce</strong> un eventuale vincolo di unicità definito a
	 * livello di database sul campo {@code classCode}. In presenza di transazioni
	 * concorrenti, infatti, due operazioni potrebbero verificare
	 * contemporaneamente l'assenza del record e tentare entrambe l'inserimento.
	 * La garanzia definitiva di unicità deve pertanto essere affidata al database
	 * tramite un opportuno vincolo {@code UNIQUE}.
	 * </p>
	 * <h3>Prestazioni</h3>
	 * <p>
	 * Essendo una query di tipo "exists", il provider JPA può tradurla in una
	 * verifica di esistenza ottimizzata, evitando il caricamento completo
	 * dell'entità e riducendo il traffico tra applicazione e database.
	 * </p>
	 * @param classCode il codice identificativo della classe ADR da ricercare;
	 * deve corrispondere al valore della proprietà {@link AdrClass#getClassCode()}.
	 * @return {@code true} se nel database esiste almeno una {@link AdrClass}
	 * avente il {@code classCode} specificato; {@code false} in caso contrario.
	 */
	boolean existsByClassCode(String classCode);
}
