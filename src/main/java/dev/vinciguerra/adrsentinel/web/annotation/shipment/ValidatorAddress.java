package dev.vinciguerra.adrsentinel.web.annotation.shipment;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.validator.AddressValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Vincolo di validazione perimetrale (Edge Validation) e sanificazione per i campi 
 * descrittivi di indirizzi fisici e toponomastica (Origine/Destinazione).
 * <p><b>Contesto Architetturale e Sicurezza (Security-by-Design):</b></p>
 * Questa annotazione funge da strato anti-corruzione (Anti-Corruption Layer) e da 
 * micro-firewall applicativo a livello di DTO. Non si limita a verificare il formato 
 * logistico, ma protegge attivamente il sistema da tentativi di iniezione di codice 
 * malevolo (es. Cross-Site Scripting - XSS) bloccando l'ingresso di tag HTML e 
 * metacaratteri non standard prima che raggiungano il Service Layer.
 * <p><b>Motore di Validazione (Constraint Composition):</b></p>
 * Sfruttando il pattern della Composizione di Annotazioni ({@code @Constraint(validatedBy = {})}), 
 * il vincolo orchestra tre livelli di controllo sequenziali:
 * <ul>
 * <li><b>Esistenza ({@code @NotNull}):</b> Garantisce che la stringa dell'indirizzo sia 
 * presente nel payload di richiesta, respingendo valori nulli.</li>
 * <li><b>Sanificazione e Sicurezza ({@code @Pattern}):</b> Attraverso una regex di 
 * <i>Blacklisting</i> ({@code ^[^<>%&$#@!^*]+$}), impone che l'indirizzo sia composto da almeno 
 * un carattere e non contenga simboli potenzialmente pericolosi o non pertinenti 
 * alla normale toponomastica (come le parentesi angolari usate nei tag script, 
 * o simboli speciali come %, $, #).</li>
 * <li><b>Integrità del Database ({@code @Size}):</b> Fissa un limite architettonico ("hard limit") 
 * di 255 caratteri. Questo è essenziale per allinearsi al classico limite dei campi 
 * {@code VARCHAR(255)} nei database relazionali (es. PostgreSQL/MySQL), prevenendo 
 * eccezioni irreversibili di <i>Data Truncation</i> durante le query di persistenza.</li>
 * </ul>
 * <p><b>Applicabilità Architetturale:</b></p>
 * Progettata per i target {@code { FIELD, PARAMETER }}, è ideale per validare le 
 * destinazioni e le origini all'interno dei Data Transfer Object (DTO) o nelle 
 * chiamate HTTP dirette.
 * @return Il messaggio di errore predefinito o personalizzato in caso di fallimento della validazione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { AddressValidator.class })
public @interface ValidatorAddress {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "Malformed payload: the required address is missing or invalid (expected 20-255 characters, no special symbols allowed).";
	/**
	 * Partiziona l'esecuzione del vincolo associandolo a specifici Validation Groups.
	 * <p>Utile per differenziare i controlli a seconda del contesto (es. Creazione vs Aggiornamento).</p>
	 * In questo caso è lasciato volutamente vuoto.
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo.
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente di allegare metadati informativi (Payload) alla violazione del vincolo, 
	 * tipicamente utilizzati per definire il livello di severità dell'errore.
	 * <p>Volutamente lasciato vuoto in questo caso.</p>
	 * @return l'array delle classi payload associate.
	 */
	Class<? extends Payload>[] payload() default {};
}
