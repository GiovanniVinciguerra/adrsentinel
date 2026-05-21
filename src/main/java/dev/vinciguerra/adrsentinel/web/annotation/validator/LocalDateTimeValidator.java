package dev.vinciguerra.adrsentinel.web.annotation.validator;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorLocalDateTime;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorLocalDateTime}.
 * Agisce come scudo architetturale (Edge Validation) per garantire che i timestamp forniti 
 * dal client siano obbligatori, semanticamente reali e strettamente allineati al formato 
 * testuale richiesto dall'infrastruttura.
 * <p><b>Design Architetturale (Strict Validation & Defense in Depth):</b></p>
 * Questa classe adotta un approccio restrittivo e una strategia di validazione a doppio strato. 
 * Rifiuta nativamente l'assenza del dato (Strict Validation) e processa la stringa in due fasi:
 * prima verifica che la data esista realmente nel calendario (delegando al motore nativo di Java), 
 * poi impone una ferrea conformità stilistica tramite un'espressione regolare. Questo isola il 
 * Service Layer da errori di deserializzazione JSON e da formati ISO spuri (es. con o senza millisecondi).
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorLocalDateTime
 */
public class LocalDateTimeValidator implements ConstraintValidator<ValidatorLocalDateTime, String> {
	/**
	 * Espressione Regolare (Regex) pre-compilata per l'analisi formale del Timestamp.
	 * <p><b>Ottimizzazione e Specifiche del Pattern:</b></p>
	 * L'istanziazione come costante {@code static final} azzera l'overhead computazionale 
	 * compilando l'automa una singola volta. Il pattern impone il formato esatto 
	 * {@code YYYY-MM-DDThh:mm:ss}, rifiutando variazioni con offset di fuso orario, 
	 * millisecondi, o mesi/giorni a singola cifra.
	 */
	private final static Pattern LOCAL_DATE_TIME_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])T([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$");
	
	/**
	 * Esegue l'ispezione profonda della stringa in ingresso per certificarne l'obbligatorietà, 
	 * la validità temporale e l'allineamento formale.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Double Check):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}, imponendo l'obbligatorietà assoluta del parametro nel payload.</li>
	 * <li><b>Validazione Semantica (Calendario):</b> Sottopone la stringa al parser nativo 
	 * {@code LocalDateTime.parse()}. Questo passaggio è vitale per intercettare date "finte" 
	 * che supererebbero la Regex (es. il "2023-02-31T10:00:00" o violazioni degli anni bisestili). 
	 * Se la data non esiste, la {@code DateTimeParseException} viene convertita in un fallimento pulito.</li>
	 * <li><b>Validazione Sintattica (Format Matching):</b> Se la data esiste, la sottopone 
	 * al motore Regex per certificare che il formato testuale inviato dal client sia 
	 * esattamente quello atteso (es. blocca date con millisecondi come "T10:00:00.000").</li>
	 * </ol>
	 * @param value La stringa rappresentante il timestamp estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} se la stringa è presente, rappresenta una data/ora realmente esistente 
	 * e rispetta il formato testuale al millimetro; {@code false} in caso di assenza, data irreale 
	 * o formato malformato.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		try {
			LocalDateTime.parse(value);
		} catch(DateTimeParseException error) {
			return false;
		}
		return LOCAL_DATE_TIME_PATTERN.matcher(value).matches();
	}
}
