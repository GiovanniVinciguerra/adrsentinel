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
}
