import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import CodeBlock from '@theme/CodeBlock';
import useBaseUrl from '@docusaurus/useBaseUrl';

import Heading from '@theme/Heading';
import styles from './index.module.css';

const quickStart = `import io.worxbend.tui.dsl.*

object Hello extends TuiApp:
  val count = Signal(0)

  override def bindings = KeyBindings(
    binding("+", "increment")(count.update(_ + 1)),
    binding("q", "quit")(quit()),
  )

  def view(using ReactiveScope): Element =
    scaffold(statusBar = Some(statusBar(bindings))) {
      centered(30, 5) {
        panel("Hello")(
          text(s"count: \${count.get}").bold.color(Color.Cyan),
          text("press + to bump it").dim,
        ).rounded
      }
    }

  def main(args: Array[String]): Unit = run().foreach(_ => ())`;

const features = [
  {
    title: 'Signals, not spaghetti',
    body: 'State lives in Signal/Computed; whatever your view reads, re-renders when it changes. No dispatch loops, no dependency arrays.',
  },
  {
    title: '40+ widgets',
    body: 'From Block and Gauge to DataTable, TextArea (undo, cluster-safe editing), DirectoryTree, Markdown, braille Charts, and a half-block Image.',
  },
  {
    title: 'App chrome built in',
    body: 'scaffold with top bar / sidebar / status line, themes, key-binding registry, screens, toasts, and a fuzzy Ctrl+P command palette.',
  },
  {
    title: 'Motion',
    body: 'A post-render effects engine (fadeIn, coalesce, typewriter, ...) with easing and combinators, plus skippable splash screens.',
  },
  {
    title: 'Mouse-aware',
    body: 'Click to focus/activate, wheel to scroll, drag sliders and split panes.',
  },
  {
    title: 'Unicode-correct',
    body: 'Display width from the Unicode Character Database: CJK, emoji ZWJ families, flags, combining marks all measure right.',
  },
  {
    title: 'Native binaries',
    body: 'Every example compiles with native-image --no-fallback and zero reflect-config, starting in milliseconds.',
  },
  {
    title: 'Testable by design',
    body: 'A headless backend + Pilot driver run full event/render cycles in plain unit tests.',
  },
];

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  const banner = useBaseUrl('banner.svg');
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <img
          src={banner}
          alt="glyphora — terminal interfaces for Scala 3"
          className={styles.wordmark}
        />
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link className="button button--secondary button--lg" to="/getting-started">
            Get started
          </Link>
          <Link
            className={clsx('button button--outline button--lg', styles.secondaryButton)}
            to="pathname:///api/">
            API reference
          </Link>
        </div>
      </div>
    </header>
  );
}

function Features() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {features.map((feature) => (
            <div key={feature.title} className={clsx('col col--3', styles.feature)}>
              <Heading as="h3">{feature.title}</Heading>
              <p>{feature.body}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function QuickStart() {
  return (
    <section className={styles.quickStart}>
      <div className="container">
        <Heading as="h2">Quick start</Heading>
        <CodeBlock language="scala" title="Hello.scala">
          {quickStart}
        </CodeBlock>
        <p>
          One import gives you every factory, the styling/layout extensions, and the
          core vocabulary. More recipes in the <Link to="/cookbook">cookbook</Link>;
          complete apps in <Link to="/examples">examples</Link>.
        </p>
      </div>
    </section>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.title}
      description="Build swaggy terminal UIs in Scala 3 — a signals-driven widget toolkit with app chrome, animations, mouse support, and first-class GraalVM native-image binaries.">
      <HomepageHeader />
      <main>
        <Features />
        <QuickStart />
      </main>
    </Layout>
  );
}
