package \${groupId}

import griffon.annotations.core.Nonnull
import griffon.annotations.inject.MVCMember
import griffon.core.artifact.GriffonView
import org.kordamp.jipsy.ServiceProviderFor
import java.beans.PropertyChangeListener
import org.codehaus.griffon.runtime.lanterna.artifact.AbstractLanternaGriffonView

@ServiceProviderFor(GriffonView)
class AppView extends AbstractLanternaGriffonView {
    @MVCMember @Nonnull
    FactoryBuilderSupport builder

    @MVCMember @Nonnull
    AppModel model

    void mvcGroupInit(Map<String, Object> args) {
        model.addPropertyChangeListener('clickCount', { evt ->
            builder.clickLabel.text = model.clickCount
        } as PropertyChangeListener)
        builder.clickLabel.text = model.clickCount
    }

    void initUI() {
        builder.with {
            application(id: 'mainWindow') {
                verticalLayout()
                label(id: 'clickLabel')
                button(clickAction)
            }
        }
    }
}