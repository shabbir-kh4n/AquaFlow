import Navbar from '../components/Navbar'
import Hero from '../components/Hero'
import TrustBar from '../components/TrustBar'
import Features from '../components/Features'
import HowItWorks from '../components/HowItWorks'
import PortalPreview from '../components/PortalPreview'
import AudienceSplit from '../components/AudienceSplit'
import Stats from '../components/Stats'
import Pricing from '../components/Pricing'
import Testimonials from '../components/Testimonials'
import FAQ from '../components/FAQ'
import CTA from '../components/CTA'
import Footer from '../components/Footer'

function Landing() {
  return (
    <div className="af-landing">
      <Navbar />
      <Hero />
      <TrustBar />
      <Features />
      <HowItWorks />
      <PortalPreview />
      <AudienceSplit />
      <Stats />
      <Pricing />
      <Testimonials />
      <FAQ />
      <CTA />
      <Footer />
    </div>
  )
}

export default Landing
