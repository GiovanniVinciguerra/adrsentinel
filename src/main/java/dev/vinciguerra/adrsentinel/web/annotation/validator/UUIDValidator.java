package dev.vinciguerra.adrsentinel.web.annotation.validator;

import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorUUID;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale e di sicurezza {@link ValidatorUUID}.
 * Agisce come scudo architetturale (Edge Validation) per garantire che gli identificatori 
 * univoci di sistema (es. ID risorse, transazioni) siano obbligatori e rispettino 
 * rigorosamente lo standard universale.
 * <p><b>Design Architetturale (Strict Validation & Performance Optimization):</b></p>
 * Questa classe è progettata per unire la massima rigidità sintattica alle massime 
 * prestazioni. Applicando un pattern a Triplo Controllo (Triple Check), non solo fonde 
 * l'obbligatorietà del dato con l'ispezione formale, ma interpone uno scudo algoritmico a 
 * complessità O(1) prima del motore Regex. Questo design blinda il server contro attacchi 
 * di tipo ReDoS (Regular Expression Denial of Service), proteggendo l'Anti-Corruption Layer 
 * dai payload malevoli di grandi dimensioni.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorUUID
 */
public class UUIDValidator implements ConstraintValidator<ValidatorUUID, String> {
	/**
	 * Espressione Regolare (Regex) pre-compilata per l'analisi formale dell'identificatore.
	 * <p><b>Ottimizzazione e Specifiche del Pattern:</b></p>
	 * L'istanziazione come costante {@code static final} azzera l'overhead computazionale 
	 * compilando l'automa a stati finiti una singola volta. Il pattern impone il formato 
	 * standard a 36 caratteri (32 cifre esadecimali divise da 4 trattini) rigorosamente 
	 * in minuscolo: {@code ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$}.
	 */
	private final static Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
	
	/**
	 * Esegue l'ispezione profonda della stringa in ingresso applicando tre livelli di sicurezza.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Triple Check):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge immediatamente 
	 * i valori {@code null}, stringhe vuote o composte solo da spazi bianchi (tramite 
	 * {@code isBlank()}), imponendo l'obbligatorietà assoluta del parametro.</li>
	 * <li><b>Fast-Fail Dimensionale (ReDoS Shield):</b> Verifica matematicamente che la 
	 * lunghezza della stringa sia <i>esattamente</i> pari a 36 caratteri. Questo controllo 
	 * a costo O(1) respinge istantaneamente stringhe troppo corte o, soprattutto, payload 
	 * giganteschi inviati per saturare il motore Regex, salvaguardando CPU e memoria.</li>
	 * <li><b>Validazione Sintattica (Format Matching):</b> Certificata la presenza e la 
	 * sicurezza dimensionale del dato, la stringa viene delegata al motore Regex per 
	 * garantire l'esatta conformità posizionale dei caratteri alfanumerici esadecimali 
	 * e dei trattini di separazione.</li>
	 * </ol>
	 * @param value La stringa rappresentante l'UUID estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se la stringa è presente, lunga esattamente 36 
	 * caratteri e conforme al formato UUID esadecimale; {@code false} in caso di assenza, 
	 * aberrazione dimensionale o formato non riconosciuto.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		if(value.length() != 36)
			return false;
		return UUID_PATTERN.matcher(value).matches();
	}
}
