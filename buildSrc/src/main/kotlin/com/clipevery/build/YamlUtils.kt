
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import com.clipevery.build.JbrReleases
import java.io.File

object YamlUtils {

    fun loadJbrReleases(file: File): JbrReleases {
        val yaml = Yaml(Constructor(JbrReleases::class.java, LoaderOptions()))
        file.inputStream().use {
            val jbrReleases = yaml.load<JbrReleases>(it)
            return jbrReleases
        }
    }
}



