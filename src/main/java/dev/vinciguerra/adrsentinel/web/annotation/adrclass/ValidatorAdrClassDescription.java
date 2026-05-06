package dev.vinciguerra.adrsentinel.web.annotation.adrclass;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Annotazione custom per la validazione strutturale e semantica della descrizione di una Classe ADR.
 * <p>
 * <b>Architettura (Constraint Composition & DRY):</b><br>
 * Questa annotazione funge da aggregatore di vincoli (Macro-Vincolo). Sfruttando il pattern della 
 * composizione tramite {@code @Constraint(validatedBy = {})}, incapsula regole di validazione standard 
 * (come la presenza obbligatoria e il limite di caratteri) in un'unica direttiva semantica. 
 * Questo approccio ripulisce i Data Transfer Object (DTO) da annotazioni ripetitive e centralizza 
 * le regole di business del dominio, garantendo il principio DRY (Don't Repeat Yourself).
 * </p>
 * <p>
 * <b>Regole di Validazione Applicate:</b><br>
 * <ul>
 * <li>{@link NotNull} : Impedisce la ricezione di payload con il campo descrizione mancante o esplicitamente nullo. 
 * <i>(Nota tecnica: valuta l'uso di @NotBlank per inibire anche le stringhe vuote o composte da soli spazi).</i></li>
 * <li>{@link Size} : Applica un hard-limit di 255 caratteri per la stringa in ingresso. Questa regola 
 * è fondamentale per la Data Integrity, in quanto previene in modo proattivo (Fail-Fast) le eccezioni 
 * di tipo {@code DataTruncationException} che si verificherebbero a livello di database SQL 
 * durante la fase di persistenza.</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (Composed Description Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({FIELD, PARAMETER})
@Constraint(validatedBy = {})
@NotNull(message = "Adr class description cannot be null")
@Size(
	max = 255,
	message = "Class description cannot exceed 255 characters."
)
public @interface ValidatorAdrClassDescription {
	/**
	 * Definisce il messaggio di errore di fallback restituito al client (HTTP 400 Bad Request) 
	 * qualora la validazione fallisca e non sia stato sovrascritto al momento dell'utilizzo.
	 * @return la stringa contenente il messaggio di errore aggregato.
	 */
	String message() default "Invalid ADR class description length or missing value.";
	/**
	 * Permette di specificare i gruppi di validazione a cui appartiene questo vincolo, 
	 * utile per applicare logiche di validazione condizionale a seconda del contesto 
	 * (es. bypassare la validazione in specifiche fasi di draft).
	 * @return l'array dei gruppi di validazione (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Permette l'assegnazione di payload personalizzati (metadati) all'errore di validazione, 
	 * utilizzabili dall'infrastruttura per mappare livelli di severità o codici di errore interni.
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
	Class<? extends Payload>[] payload() default {};
}
