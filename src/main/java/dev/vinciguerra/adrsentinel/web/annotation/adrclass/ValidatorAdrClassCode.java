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
import jakarta.validation.constraints.Pattern;

/**
 * Annotazione custom per la validazione formale del Codice Classe ADR.
 * <p>
 * <b>Architettura (Constraint Composition):</b><br>
 * Questa annotazione agisce da "Macro-Vincolo". Sfrutta il pattern della composizione 
 * (tramite {@code @Constraint(validatedBy = {})}) per delegare il motore di validazione 
 * all'annotazione nativa {@link Pattern}. Questo garantisce il principio DRY (Don't Repeat Yourself) 
 * e centralizza la regola di business del dominio ADR in un unico punto del codice.
 * </p>
 * <p>
 * <b>Regole di validazione</b><br>
 * Vengono applicate le seguenti restrizioni:
 * <ul>
 * <li><b>Controllo valori</b>: non consentiti valori {@code null}</li>
 * <li><b>Motore Regex</b>: non consentiti valori che non adottano un formato valido</li>
 * </ul>
 * </p>
 * <p>
 * <b>Regole Motore Regex:</b><br>
 * L'espressione regolare {@code ^(?=.{1,4}$)\d(\.\d+)?[a-zA-Z]?$} applica le seguenti restrizioni:
 * <ul>
 * <li>{@code (?=.{1,4}$)} : <b>Lunghezza massima:</b> Impone un hard-limit di 4 caratteri totali.</li>
 * <li>{@code \d} : <b>Classe Primaria:</b> Deve iniziare obbligatoriamente con una (e una sola) cifra (es. "3").</li>
 * <li>{@code (\.\d+)?} : <b>Sotto-classe (Opzionale):</b> Può essere seguita da un punto e da numeri (es. ".1").</li>
 * <li>{@code [a-zA-Z]?$} : <b>Lettera di Compatibilità (Opzionale):</b> Può terminare con una singola lettera, 
 * tipica degli esplosivi della Classe 1 (es. "S" in "1.4S").</li>
 * </ul>
 * </p>
 * <p>
 * <b>Esempi di Validazione:</b><br>
 * <i>Validi (Hit):</i> "3", "4.1", "5.2", "1.4S", "9A"<br>
 * <i>Scartati (Miss):</i> "10" (inizia con due cifre), "4.1.1" (troppi punti), "1.4AB" (supera i 4 caratteri)
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({FIELD, PARAMETER})
@Constraint(validatedBy = {})
@NotNull(message = "Adr class code cannot be null")
@Pattern(
	regexp = "^(?=.{1,4}$)\\d(\\.\\d+)?[a-zA-Z]?$",
	message = "Invalid: max 4 chars. Format: 1 digit + optional '.numbers' + optional letter."
)
public @interface ValidatorAdrClassCode {
	/**
	 * Definisce il messaggio di errore predefinito restituito al client (HTTP 400) 
	 * qualora il valore fornito non rispetti il formato ADR.
	 * @return la stringa contenente il messaggio di errore.
	 */
	String message() default "Invalid ADR class format or missing code.";
	/**
	 * Permette di specificare i gruppi di validazione a cui appartiene questo vincolo.
	 * Utilizzato per applicare logiche di validazione condizionale (es. validare diversamente 
	 * in fase di Creazione rispetto alla fase di Aggiornamento).
	 * @return l'array dei gruppi di validazione (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Permette all'architettura di sistema di assegnare payload personalizzati (metadati) 
	 * all'errore di validazione (es. livelli di severità come INFO, WARNING, FATAL).
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
	Class<? extends Payload>[] payload() default {};
}
