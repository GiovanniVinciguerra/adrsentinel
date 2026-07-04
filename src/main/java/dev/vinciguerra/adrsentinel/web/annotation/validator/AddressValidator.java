package dev.vinciguerra.adrsentinel.web.annotation.validator;

import java.util.regex.Pattern;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorAddress;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione composito e di sicurezza {@link ValidatorAddress}.
 * Agisce come scudo perimetrale avanzato (Edge Validation) per gli indirizzi fisici, 
 * unendo la verifica di presenza, il rispetto dei limiti fisici di archiviazione e 
 * una rigorosa sanitizzazione del dato.
 * <p><b>Design Architetturale (Strict Validation & Defense in Depth):</b></p>
 * Trattandosi di un campo a testo libero (Free-Text Field), l'indirizzo rappresenta un 
 * vettore di attacco primario per le iniezioni di codice. Questa classe implementa una 
 * difesa in profondità a tre strati: impedisce l'assenza del dato, impone una lunghezza 
 * minima e massima per garantire consistenza informativa, e respinge proattivamente 
 * i caratteri speciali comunemente utilizzati per le iniezioni SQL o HTML.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorAddress
 */
public class AddressValidator implements ConstraintValidator<ValidatorAddress, String> {
	/**
	 * Espressione Regolare (Regex) pre-compilata per la sanitizzazione e sicurezza del testo.
	 * <p><b>Ottimizzazione e Specifiche del Pattern (Anti-XSS):</b></p>
	 * L'istanziazione come costante {@code static final} azzera l'overhead computazionale, 
	 * compilando l'automa a stati finiti una singola volta. Il pattern {@code ^[^<>%&$#@!^*]+$} 
	 * opera per <i>esclusione</i>: accetta qualsiasi carattere alfanumerico o di punteggiatura 
	 * standard necessario per un indirizzo, ma respinge istantaneamente la stringa se rileva 
	 * parentesi angolari ({@code <>}, usate nei tag di script) o simboli speciali potenzialmente 
	 * nocivi ({@code %&$#@!^*}).
	 */
	private final static Pattern ADDRESS_PATTERN = Pattern.compile("^[^<>%&$#@!^*]+$");
	
	/**
	 * Esegue l'ispezione profonda della stringa in ingresso applicando i tre livelli di sicurezza.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Triple Check):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge immediatamente 
	 * valori {@code null}, stringhe vuote o composte esclusivamente da spazi bianchi 
	 * (tramite {@code isBlank()}), imponendo l'obbligatorietà assoluta del campo.</li>
	 * <li><b>Boundary Check (Limiti Dimensionali):</b> Verifica che la lunghezza della 
	 * stringa rientri tassativamente nel range [20, 255]. Questo previene l'inserimento di 
	 * indirizzi troppo corti per essere veritieri (es. "Via Po, 1") o troppo lunghi, 
	 * scongiurando errori di troncamento nel database o tentativi di Buffer Overflow.</li>
	 * <li><b>Security Sanitization (Regex Matching):</b> Sottopone la stringa, ormai 
	 * valorizzata e dimensionata, al motore Regex per bloccare definitivamente payload anomali 
	 * o tentativi di Cross-Site Scripting (XSS).</li>
	 * </ol>
	 * @param value La stringa rappresentante l'indirizzo estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se la stringa è presente, rientra nei limiti 
	 * dimensionali [20-255] e non contiene simboli vietati; {@code false} in caso di violazione 
	 * di uno qualsiasi dei tre livelli di sicurezza.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		if(value.length() < 20 || value.length() > 255)
			return false;
		return ADDRESS_PATTERN.matcher(value).matches();
	}
}
