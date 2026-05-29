package dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.validator;

import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorGeometry;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del motore di validazione per il vincolo cartografico spaziale {@link ValidatorGeometry}.
 * <p>
 * Questa classe viene istanziata come Singleton dal framework di validazione (es. Hibernate Validator) 
 * per analizzare i campi stringa destinati a contenere la rotta vettoriale. Agisce come uno 
 * <b>Scudo Difensivo (Defensive Shield)</b> multilivello contro payload malformati, attacchi di 
 * tipo XSS (Cross-Site Scripting) e tentativi di Denial of Service (DoS).
 * </p>
 * <p>
 * <b>Responsabilità Architetturali:</b>
 * <ul>
 * <li><b>Strict Presence:</b> Applica una politica di tolleranza zero verso dati assenti o vuoti (Blank).</li>
 * <li><b>Payload Size Protection:</b> Impone un limite vitale (1.000.000 di caratteri) alla dimensione 
 * del dato, collaborando con la configurazione di sicurezza del {@code JsonMapper} per sventare attacchi 
 * di saturazione della memoria (OOM - Out Of Memory).</li>
 * <li><b>Domain Algorithm Format:</b> Verifica la conformità crittografica della stringa all'algoritmo 
 * standard <i>Google Encoded Polyline Format</i>, bloccando qualsiasi carattere non previsto dalla matematica del formato.</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorGeometry
 */
public class GeometryValidator implements ConstraintValidator<ValidatorGeometry, String> {
	/**
	 * Espressione regolare precompilata (Thread-Safe e ottimizzata per le performance) 
	 * atta a validare il formato strutturale della Polyline.
	 * <p>
	 * L'algoritmo di codifica spaziale (Encoded Polyline) cifra le coordinate applicando un offset 
	 * matematico. Di conseguenza, i caratteri validi appartengono <b>ESCLUSIVAMENTE</b> al blocco ASCII 
	 * compreso tra \x3F (il carattere '?') e \x7E (il carattere '~'). Qualsiasi spazio, tabulazione, 
	 * lettera accentata o carattere di controllo esterno a questo range denota un dato corrotto 
	 * o un potenziale vettore di attacco (Injection).
	 * </p>
	 */
	private static final Pattern POLYLINE_REGEX = Pattern.compile("^[\\x3F-\\x7E]+$");
	
	/**
	 * Valuta se la stringa fornita rappresenta una geometria vettoriale codificata strutturalmente 
	 * e quantitativamente valida.
	 * <p>
	 * <b>Flusso di Validazione (Execution Flow):</b>
	 * <ol>
	 * <li><b>Strict Mode:</b> Intercettazione immediata del valore assente ({@code null}) o composto 
	 * da soli spazi bianchi ({@code isBlank()}).</li>
	 * <li><b>Sanity Check (DoS Protection):</b> Blocco di stringhe che superano la soglia critica di 
	 * 1.000.000 di byte, prevenendo il <i>Payload Bombing</i>.</li>
	 * <li><b>Domain Algorithm Check:</b> Esecuzione del pattern matching per verificare il rigoroso 
	 * rispetto dell'alfabeto ASCII consentito.</li>
	 * </ol>
	 * </p>
	 * @param value La stringa contenente l'encoded Polyline da validare.
	 * @param context Il contesto di esecuzione fornito dal framework di validazione.
	 * @return {@code true} se la stringa è presente, nei limiti di dimensione e crittograficamente intatta; 
	 * {@code false} in caso di assenza, superamento dei limiti di memoria o presenza di caratteri illegali.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		if(value.length() > 1000000)
			return false;
		return POLYLINE_REGEX.matcher(value).matches();
	}
}
