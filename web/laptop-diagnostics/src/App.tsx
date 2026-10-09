import { Toaster } from '@/components/ui/toaster';
import { TooltipProvider } from '@/components/ui/tooltip';
import NotFound from '@/pages/not-found';
import { Route, Switch, Router as WouterRouter } from 'wouter';
import { ThemeProvider } from '@/components/theme-provider';
import { TestProvider } from '@/context/TestContext';
import { Layout } from '@/components/Layout';

// Pages
import { LandingPage } from '@/pages/LandingPage';
import { Dashboard } from '@/pages/Dashboard';
import { ResultsSummary } from '@/pages/ResultsSummary';
import { KeyboardTest } from '@/pages/tests/KeyboardTest';
import { MouseTest } from '@/pages/tests/MouseTest';
import { CameraTest } from '@/pages/tests/CameraTest';
import { MicrophoneTest } from '@/pages/tests/MicrophoneTest';
import { SpeakerTest } from '@/pages/tests/SpeakerTest';
import { DisplayTest } from '@/pages/tests/DisplayTest';
import { BatteryTest } from '@/pages/tests/BatteryTest';
import { NetworkTest } from '@/pages/tests/NetworkTest';
import { SensorsTest } from '@/pages/tests/SensorsTest';

function Router() {
  return (
    <Switch>
      {/* Landing page without the Layout wrapper */}
      <Route path="/" component={LandingPage} />

      {/* All other pages use the shared Layout */}
      <Route>
        <Layout>
          <Switch>
            <Route path="/dashboard" component={Dashboard} />
            <Route path="/results" component={ResultsSummary} />
            <Route path="/test/keyboard" component={KeyboardTest} />
            <Route path="/test/mouse" component={MouseTest} />
            <Route path="/test/camera" component={CameraTest} />
            <Route path="/test/microphone" component={MicrophoneTest} />
            <Route path="/test/speaker" component={SpeakerTest} />
            <Route path="/test/display" component={DisplayTest} />
            <Route path="/test/battery" component={BatteryTest} />
            <Route path="/test/network" component={NetworkTest} />
            <Route path="/test/sensors" component={SensorsTest} />
            <Route component={NotFound} />
          </Switch>
        </Layout>
      </Route>
    </Switch>
  );
}

function App() {
  return (
    <ThemeProvider attribute="class" defaultTheme="light" enableSystem={false} disableTransitionOnChange>
      <TestProvider>
        <TooltipProvider>
          <WouterRouter base={import.meta.env.BASE_URL.replace(/\/$/, '')}>
            <Router />
          </WouterRouter>
          <Toaster />
        </TooltipProvider>
      </TestProvider>
    </ThemeProvider>
  );
}

export default App;
