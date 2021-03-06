package org.eclipse.buildship.ui.workspace

import org.gradle.tooling.CancellationToken
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.eclipse.EclipseProject

import com.gradleware.tooling.toolingclient.GradleDistribution
import com.gradleware.tooling.toolingmodel.repository.FetchStrategy

import org.eclipse.core.commands.Command
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.core.expressions.IEvaluationContext
import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jface.viewers.StructuredSelection

import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.configuration.BuildConfiguration
import org.eclipse.buildship.core.configuration.WorkspaceConfiguration
import org.eclipse.buildship.core.event.Event
import org.eclipse.buildship.core.event.EventListener
import org.eclipse.buildship.core.workspace.GradleNatureAddedEvent
import org.eclipse.buildship.ui.test.fixtures.EclipseProjects
import org.eclipse.buildship.ui.test.fixtures.WorkspaceSpecification

class AddBuildshipNatureHandlerTest extends WorkspaceSpecification {

    def "Uses configuration from workspace settings"() {
        setup:
        WorkspaceConfiguration originalWorkspaceConfig = configurationManager.loadWorkspaceConfiguration()
        WorkspaceConfiguration config = new WorkspaceConfiguration(GradleDistribution.forVersion("3.0"), dir('custom-gradle-home'), false, false)
        configurationManager.saveWorkspaceConfiguration(config)

        IProject project = EclipseProjects.newProject('add-buildship-nature')
        project.getFile("settings.gradle").create(new ByteArrayInputStream("".bytes), true, new NullProgressMonitor())
        waitForResourceChangeEvents()

        when:
        AddBuildshipNatureHandler handler = new AddBuildshipNatureHandler()
        handler.execute(projectSelectionEvent(project))
        waitForGradleJobsToFinish()

        then:
        eclipseModelLoadedWithWorkspacePreferences(project.location.toFile())

        cleanup:
        configurationManager.saveWorkspaceConfiguration(originalWorkspaceConfig)
    }

    def "Publishes 'nature added' event"() {
        setup:
        IProject project = EclipseProjects.newProject('test-nature-added-event')
        waitForResourceChangeEvents()

        TestEventListener eventListener = new TestEventListener()
        CorePlugin.listenerRegistry().addEventListener(eventListener)

        when:
        AddBuildshipNatureHandler handler = new AddBuildshipNatureHandler()
        handler.execute(projectSelectionEvent(project))
        waitForGradleJobsToFinish()
        GradleNatureAddedEvent event = eventListener.events.find { it instanceof GradleNatureAddedEvent }

        then:
        event.projects == [project] as Set

        cleanup:
        CorePlugin.listenerRegistry().removeEventListener(eventListener)
    }

    private ExecutionEvent projectSelectionEvent(IProject selection) {
        IEvaluationContext context = Mock(IEvaluationContext)
        context.getVariable(_) >> new StructuredSelection(selection)
        ExecutionEvent event = new ExecutionEvent(new Command(''), [:], null, context)
        event
    }

    private boolean eclipseModelLoadedWithWorkspacePreferences(File projectLocation) {
        BuildConfiguration buildConfig = createInheritingBuildConfiguration(projectLocation)
        CancellationToken token = GradleConnector.newCancellationTokenSource().token()
        IProgressMonitor monitor = new NullProgressMonitor()
        return CorePlugin.gradleWorkspaceManager().getGradleBuild(buildConfig).getModelProvider().fetchModels(EclipseProject.class, FetchStrategy.FROM_CACHE_ONLY, token, monitor) != null
    }

    private class TestEventListener implements EventListener {

        List events = []

        @Override
        public void onEvent(Event event) {
            events += event
        }

    }
}
