package dev.vinciguerra.adrsentinel.web.annotation.validator;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorLocalDate;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorLocalDate}.
 * Agisce come scudo architetturale (Edge Validation) per garantire che le date fornite 
 * dal client siano obbligatorie, semanticamente reali e strettamente allineate allo standard 
 * testuale ISO 8601 (YYYY-MM-DD).
 * <p><b>Design Architetturale (Strict Validation & Defense in Depth):</b></p>
 * In perfetta simmetria con i validatori di timestamp, questa classe adotta un approccio 
 * restrittivo e una strategia di validazione a doppio strato. Rifiuta nativamente l'assenza 
 * del dato e processa la stringa in due fasi: prima verifica che la data esista concretamente 
 * nel calendario (delegando al motore nativo di Java), poi impone una ferrea conformità stilistica 
 * tramite un'espressione regolare. Questo blinda il Service Layer contro bug legati agli anni bisestili 
 * e previene la propagazione di formati ambigui o non standardizzati.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorLocalDate
 */
public class LocalDateValidator implements ConstraintValidator<ValidatorLocalDate, String> {
	/**
	 * Espressione Regolare (Regex) pre-compilata per l'analisi formale della data pura.
	 * <p><b>Ottimizzazione e Specifiche del Pattern:</b></p>
	 * L'istanziazione come costante {@code static final} azzera l'overhead computazionale 
	 * compilando l'automa una singola volta. Il pattern impone il formato esatto 
	 * {@code YYYY-MM-DD}, rifiutando variazioni contenenti orari, offset di fuso orario, 
	 * o formattazioni con mesi/giorni a singola cifra (es. "2023-5-1").
	 */
	private final static Pattern LOCAL_DATE_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$");
	
	/**
	 * Esegue l'ispezione profonda della stringa in ingresso per certificarne l'obbligatorietà, 
	 * la validità temporale e l'allineamento formale.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Double Check):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}, imponendo l'obbligatorietà assoluta del parametro nel payload.</li>
	 * <li><b>Validazione Semantica (Calendario):</b> Sottopone la stringa al parser nativo 
	 * {@code LocalDate.parse()}. Questo passaggio è cruciale per intercettare aberrazioni 
	 * temporali che supererebbero la Regex (es. il "2023-02-31" o il "2023-04-31"). 
	 * Se la data non esiste sul calendario, la {@code DateTimeParseException} viene convertita 
	 * in un pulito fallimento di validazione.</li>
	 * <li><b>Validazione Sintattica (Format Matching):</b> Certificata l'esistenza reale 
	 * della data, la stringa viene passata al motore Regex per assicurare che il formato 
	 * testuale inviato dal client coincida esattamente con la maschera attesa dall'infrastruttura.</li>
	 * </ol>
	 * @param value La stringa rappresentante la data estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} se la stringa è presente, rappresenta una data realmente esistente 
	 * e rispetta il formato testuale al millimetro; {@code false} in caso di assenza, data irreale 
	 * o formato testuale malformato.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		try {
			LocalDate.parse(value);
		} catch(DateTimeParseException error) {
			return false;
		}
		return LOCAL_DATE_PATTERN.matcher(value).matches();
	}
}
