package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator.PackageWeightValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Jakarta Bean Validation) specifica per il dominio ADR.
 * Viene utilizzata per marcare in modo dichiarativo i campi o i parametri che rappresentano 
 * il peso della tara dell'imballaggio (packaging weight / tare).
 * <p><b>Ruolo Architetturale (Boundary Protection &amp; Mathematical Integrity):</b></p>
 * L'annotazione funge da aggancio per la logica di controllo implementata in {@link PackageWeightValidator}. 
 * Posizionata sui DTO di richiesta REST o sui parametri esposti dai Controller, intercetta e respinge 
 * immediatamente i payload strutturalmente non conformi (HTTP 400 Bad Request). Questo previene sia la 
 * persistenza di dati sporchi sul database, sia la propagazione di anomalie aritmetiche verso gli algoritmi 
 * di calcolo del carico o i motori GIS/routing a valle.
 * <p><b>Invarianti di Dominio e Soglie Fisiche:</b></p>
 * Il vincolo impone restrizioni matematiche e fisiche rigide basate sull'accordo normativo ADR:
 * <ul>
 * <li><b>Integrità IEEE 754:</b> Rifiuta categoricamente valori non definiti ({@code NaN}) o infiniti 
 * ({@code Infinity}), bloccando vulnerabilità computazionali ed errori di overflow.</li>
 * <li><b>Limite Fisico Superiore:</b> Fissa un tetto massimo invalicabile a 500.0 kg. Questa soglia 
 * copre ampiamente le tare strutturali delle cisternette (IBC) e dei grandi imballaggi metallici omologati ONU, 
 * agendo al contempo da barriera contro errori macroscopici di digitazione (typos), come l'inserimento accidentale 
 * del peso lordo della merce nel campo riservato alla sola tara dell'involucro.</li>
 * <li><b>Flessibilità della Base:</b> Consente il valore {@code 0.0f} unicamente per assecondare la sintassi di trasporto 
 * per merci alla rinfusa ({@code UNPACKAGED}) o in cisterna ({@code TANK}), dove la tara del collo non esiste. 
 * Le validazioni di interdipendenza incrociata (es. bloccare lo zero se la merce è imballata in un fusto) sono 
 * demandate alle fasi successive del ciclo di vita dell'entità.</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see PackageWeightValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { PackageWeightValidator.class })
public @interface ValidatorPackageWeight {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required numeric value is missing or invalid (expected a strictly positive, finite number).";
	/**
	 * Partiziona l'esecuzione del vincolo associandolo a specifici gruppi di validazione 
	 * (Validation Groups).
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente all'architettura di sistema di allegare metadati informativi (Payload) 
	 * alla violazione del vincolo, utili per tracciare livelli di severità dell'errore (es. WARNING, FATAL).
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
	Class<? extends Payload>[] payload() default {};
}
