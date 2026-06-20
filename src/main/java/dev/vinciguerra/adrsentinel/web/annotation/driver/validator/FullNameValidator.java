package dev.vinciguerra.adrsentinel.web.annotation.driver.validator;

import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorFullName;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Classe validatrice per l'annotazione di vincolo personalizzata {@link ValidatorFullName}.
 * <p>
 * Questo validatore assicura che una determinata stringa rappresentante un nome e cognome 
 * sia strutturalmente valida e sicura per la persistenza. Applica i seguenti criteri:
 * <ul>
 * <li>La stringa non è {@code null} né completamente vuota o composta solo da spazi.</li>
 * <li>La lunghezza della stringa (esclusi gli spazi iniziali e finali) è di almeno 3 caratteri.</li>
 * <li>La lunghezza complessiva della stringa non supera i 255 caratteri.</li>
 * <li>La stringa contiene esclusivamente caratteri validi: caratteri alfabetici 
 * (di qualsiasi lingua, supportati tramite la proprietà Unicode), spazi, 
 * trattini ({@code -}) e underscore ({@code _}).</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorFullName
 */
public class FullNameValidator implements ConstraintValidator<ValidatorFullName, String> {
	/**
	 * Espressione regolare compilata utilizzata per validare i caratteri consentiti nel nome completo.
	 * <p>
	 * Dettaglio del pattern:
	 * <ul>
	 * <li>{@code ^} - Corrisponde all'inizio della stringa.</li>
	 * <li>{@code \p{L}} - Corrisponde a qualsiasi tipo di lettera di qualsiasi lingua.</li>
	 * <li>{@code  _-} - Corrisponde a spazi, underscore e trattini.</li>
	 * <li>{@code +} - Richiede uno o più dei caratteri consentiti.</li>
	 * <li>{@code $} - Corrisponde alla fine della stringa.</li>
	 * </ul>
	 */
	private final static Pattern FULL_NAME_PATTERN = Pattern.compile("^[\\p{L} _-]+$");
	
	/**
	 * Valida la stringa del nome completo fornita in base alle regole definite.
	 * @param value la rappresentazione in formato stringa del nome e cognome da validare
	 * @param context informazioni contestuali e API per il processo di validazione
	 * @return {@code true} se il nome completo rispetta tutte le regole di validazione; 
	 * {@code false} se il valore è {@code null}, vuoto, non rispetta i limiti di lunghezza, 
	 * o contiene caratteri non consentiti (es. numeri o simboli speciali).
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		if(value.trim().length() < 4 || value.length() > 255)
			return false;
		return FULL_NAME_PATTERN.matcher(value).matches();
	}
}
