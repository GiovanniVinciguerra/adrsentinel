package dev.vinciguerra.adrsentinel.web.annotation.waybill.validator;

import java.util.regex.Pattern;

import dev.vinciguerra.adrsentinel.web.annotation.waybill.ValidatorFilename;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del validatore custom legato all'annotazione {@link ValidatorFilename}.
 * <p><b>Contesto Architetturale (Zero-Trust &amp; OS Safety):</b></p>
 * Questa classe agisce come scudo perimetrale (Boundary Layer) per neutralizzare 
 * tentativi di iniezione a livello di file system. Sfruttando un approccio a 
 * "Whitelist" rigida, ignora le deboli liste di blocco (blacklist) dei singoli sistemi 
 * operativi, ammettendo esclusivamente un sottoinsieme universale e sicuro di caratteri.
 * <p><b>Sicurezza e Prevenzione Vulnerabilità (OWASP):</b></p>
 * <ul>
 * <li><b>Path Traversal / LFI:</b> L'assenza del punto (al di fuori dell'estensione) e dello slash 
 * impedisce nativamente la navigazione abusiva delle directory (es. {@code ../../../etc/passwd}).</li>
 * <li><b>OS Command Injection:</b> Blocca caratteri speciali e spazi che potrebbero essere mal 
 * interpretati dai processori a riga di comando (es. bash, cmd) in caso di script automatizzati.</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see ValidatorFilename
 */
public class FilenameValidator implements ConstraintValidator<ValidatorFilename, String> {
	/**
	 * Motore Regex precompilato e thread-safe basato su logica Whitelist.
	 * <ul>
	 * <li>{@code ^(?=.{5,255}$)} - <i>Lookahead Dimensionale:</i> Impone che l'intera stringa misuri 
	 * tra 5 e 255 caratteri (limite standard NTFS/ext4).</li>
	 * <li>{@code [a-zA-Z0-9_\\-]+} - <i>Body Whitelist:</i> Ammette solo caratteri alfanumerici, 
	 * trattini (dash) e trattini bassi (underscore).</li>
	 * <li>{@code \\.[a-zA-Z0-9]{3,4}$} - <i>Extension Whitelist:</i> Esige un singolo punto separatore 
	 * seguito da un'estensione rigida di 3 o 4 caratteri alfanumerici, al momento solo .pdf è valido.</li>
	 * </ul>
	 */
	private final static Pattern FILENAME_PATTERN = Pattern.compile("^(?=.{5,255}$)[a-zA-Z0-9_\\-]+\\.pdf$");
	
	/**
	 * Esegue la validazione formale e di sicurezza sulla stringa in ingresso.
	 * <p><b>Pipeline di Validazione (Defense in Depth &amp; Fail-Fast):</b></p>
	 * <ol>
	 * <li><i>Optional Field Support:</i> Se il valore è {@code null}, la validazione passa ({@code true}). 
	 * L'obbligatorietà del campo è delegata ad annotazioni standard come {@code @NotNull}, 
	 * garantendo il principio di singola responsabilità (SRP).</li>
	 * <li><i>Blank Rejection:</i> Stringhe vuote o composte da soli spazi vengono respinte.</li>
	 * <li><i>ReDoS Prevention:</i> Il controllo esplicito sulla lunghezza ({@code > 255}) agisce come 
	 * Fail-Fast primitivo. Previene sovraccarichi della CPU bloccando payload malevoli giganteschi 
	 * prima ancora di scomodare il motore delle Espressioni Regolari.</li>
	 * <li><i>Regex Delegation:</i> Validazione semantica finale tramite il pattern a Whitelist.</li>
	 * </ol>
	 * @param value Il nome del file (fornito dal client HTTP) da sottoporre a verifica.
	 * @param context Il contesto strutturale fornito dal framework di validazione Jakarta.
	 * @return {@code true} se la stringa è nulla oppure sintatticamente innocua per il file system; 
	 * {@code false} in caso di violazione dei parametri di sicurezza.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null)
			return true;
		if(value.isBlank())
			return false;
		if(value.length() > 255)
			return false;
		return FILENAME_PATTERN.matcher(value).matches();
	}
}
